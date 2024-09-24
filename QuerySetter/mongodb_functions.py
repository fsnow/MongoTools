# mongodb_functions.py

from pymongo import MongoClient
from collections import defaultdict
import traceback
import logging

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def safe_execute(func):
    def wrapper(*args, **kwargs):
        try:
            return func(*args, **kwargs)
        except Exception as e:
            error_msg = f"Error in {func.__name__}: {str(e)}"
            logger.error(error_msg)
            logger.error(traceback.format_exc())
            return None
    return wrapper

@safe_execute
def get_mongodb_version(client):
    version_string = client.server_info()['version']
    major_version = int(version_string.split('.')[0])
    return major_version

@safe_execute
def get_debug_query_shapes(client, db_name, coll_name):
    admin_db = client['admin']
    pipeline = [
        {"$querySettings": {"showDebugQueryShape": True}},
        {"$match": {"debugQueryShape.cmdNs.db": db_name, "debugQueryShape.cmdNs.coll": coll_name}}
    ]
    return list(admin_db.aggregate(pipeline))

@safe_execute
def get_stages_r(o, arr):
    if o and 'queryPlanner' in o:
        get_stages_r(o['queryPlanner'], arr)
    elif o and 'winningPlan' in o:
        get_stages_r(o['winningPlan'], arr)
    elif o and 'queryPlan' in o:
        get_stages_r(o['queryPlan'], arr)
    elif o and 'stage' in o:
        arr.insert(0, o['stage'])
        if 'inputStage' in o:
            get_stages_r(o['inputStage'], arr)
        elif 'inputStages' in o:
            for e in o['inputStages']:
                get_stages_r(e, arr)
    elif o and 'stages' in o and o['stages'] and '$cursor' in o['stages'][0]:
        get_stages_r(o['stages'][0]['$cursor'], arr)

@safe_execute
def get_plan_stages(plan):
    arr = []
    get_stages_r(plan, arr)
    return arr

@safe_execute
def get_query_stats(client):
    MATCH_STAGE = {
        "$match": {"key.queryShape.cmdNs.db": {"$nin": ["admin", "config", "local"]}}
    }
    SORT_STAGE = {
        "$sort": {"key.queryShape.cmdNs.db": 1, "key.queryShape.cmdNs.coll": 1}
    }

    admin_db = client['admin']
    cursor = admin_db.aggregate([
        {"$queryStats": {}},
        MATCH_STAGE,
        SORT_STAGE
    ])

    query_stats = defaultdict(list)
    for doc in cursor:
        query_shape = doc['key']['queryShape']
        db_name = query_shape['cmdNs']['db']
        coll_name = query_shape['cmdNs']['coll']
        namespace = f"{db_name}.{coll_name}"
        query_stats[namespace].append(doc)

    return query_stats

@safe_execute
def get_indexes(client, namespace):
    db_name, coll_name = namespace.split('.')
    return list(client[db_name][coll_name].list_indexes())

@safe_execute
def get_explain_plan(client, entry):
    query_shape = entry['key']['queryShape']
    db_name = query_shape['cmdNs']['db']
    coll_name = query_shape['cmdNs']['coll']
    command = query_shape['command']
    coll = client[db_name][coll_name]

    if command == "find":
        return coll.find(query_shape['filter']).explain()
    elif command == "aggregate":
        return client[db_name].command({
            "aggregate": coll_name,
            "pipeline": query_shape['pipeline'],
            "explain": True
        })
    # coll.aggregate(query_shape['pipeline']).explain()
    return None

@safe_execute
def simplify_filter(filter_doc):
    if isinstance(filter_doc, dict):
        simplified = {}
        for key, value in filter_doc.items():
            if key == '$and' or key == '$or':
                simplified[key] = [simplify_filter(item) for item in filter_doc[key]]
            elif isinstance(value, dict) and any(k.startswith('$') for k in value.keys()):
                op = next(k for k in value.keys() if k.startswith('$'))
                simplified[key] = op  # Keep the operator for later analysis
            elif isinstance(value, str) and value.startswith('?'):
                simplified[key] = '$eq'  # Treat as equality
            else:
                simplified[key] = simplify_filter(value)
        return simplified
    elif isinstance(filter_doc, list):
        return [simplify_filter(item) for item in filter_doc]
    else:
        return filter_doc

@safe_execute
def extract_fields(doc):
    fields = []
    if isinstance(doc, dict):
        for key, value in doc.items():
            if key not in ['$and', '$or']:
                fields.append((key, value))
            fields.extend(extract_fields(value))
    elif isinstance(doc, list):
        for item in doc:
            fields.extend(extract_fields(item))
    return fields

@safe_execute
def suggest_index(filter_doc, sort_doc):
    simplified_filter = simplify_filter(filter_doc)
    equality_fields = []
    range_fields = []
    
    for field, value in extract_fields(simplified_filter):
        if field not in equality_fields and field not in range_fields:
            if value == '$eq':
                equality_fields.append(field)
            else:
                range_fields.append(field)
    
    sort_fields = list(sort_doc.keys()) if sort_doc else []
    
    # Remove duplicate fields
    sort_fields = [field for field in sort_fields if field not in equality_fields and field not in range_fields]
    
    # Combine fields according to Equality, Sort, Range rule
    suggested_index = {}
    for field in equality_fields + sort_fields + range_fields:
        suggested_index[field] = 1
    
    return suggested_index

@safe_execute
def has_collscan(stages):
    return any('COLLSCAN' in stage for stage in stages)

@safe_execute
def create_representative_query(query_doc):
    if isinstance(query_doc, dict):
        rep_query = {}
        for key, value in query_doc.items():
            if isinstance(value, str) and value.startswith('?'):
                if value == '?number':
                    rep_query[key] = 1
                elif value == '?string':
                    rep_query[key] = "a"
                elif value == '?date':
                    rep_query[key] = {"$date": "2024-01-01T00:00:00.000Z"}
                elif value == '?objectId':
                    rep_query[key] = {"$oid": "000000000000000000000000"}
                else:
                    rep_query[key] = value  # Keep other placeholders as is for now
            elif isinstance(value, dict):
                rep_query[key] = create_representative_query(value)
            elif isinstance(value, list):
                rep_query[key] = [create_representative_query(item) for item in value]
            else:
                rep_query[key] = value
        return rep_query
    elif isinstance(query_doc, list):
        return [create_representative_query(item) for item in query_doc]
    else:
        return query_doc

@safe_execute
def create_rejection_filter(query_shape, rep_query):
    command = query_shape.get('command', 'Unknown')
    db_name = query_shape['cmdNs']['db']
    coll_name = query_shape['cmdNs']['coll']
    sort = query_shape.get('sort')

    rejection_filter = {
        "setQuerySettings": {
            command: coll_name,
            "filter": rep_query,
            "$db": db_name
        },
        "settings": {
            "reject": True
        }
    }

    if sort:
        rejection_filter["setQuerySettings"]["sort"] = sort

    return rejection_filter
