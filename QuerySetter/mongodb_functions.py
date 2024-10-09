from pymongo import MongoClient
from uuid import uuid4
from collections import defaultdict
import traceback
import logging
import hashlib
import json
import re

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
    # Check the internalQueryStatsRateLimit parameter
    param_state = client.admin.command({'getParameter': 1, 'internalQueryStatsRateLimit': 1})
    rate_limit = param_state.get('internalQueryStatsRateLimit')

    if rate_limit == 0:
        return {"error": "rate_limit_zero"}

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

    if not query_stats:
        return {"error": "empty_query_stats"}

    return query_stats

@safe_execute
def get_indexes(client, namespace):
    db_name, coll_name = namespace.split('.')
    return list(client[db_name][coll_name].list_indexes())

@safe_execute
def get_index_info(client, db_name, coll_name, index_name):
    db = client[db_name]
    coll = db[coll_name]
    indexes = list(coll.list_indexes())
    for index in indexes:
        if index['name'] == index_name:
            return index
    return None

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
                elif value == '?bool':
                    rep_query[key] = True
                elif value == '?null':
                    rep_query[key] = None
                elif value == '?object':
                    rep_query[key] = {"a": 1}
                elif value == '?binData':
                    rep_query[key] = {"$binary": {"base64": "YQ==", "subType": "00"}}
                elif value == '?timestamp':
                    rep_query[key] = { "$timestamp": { "t": 1677749825, "i": 20 } }
                elif value == '?minKey':
                    rep_query[key] = { '$minKey': 1 }
                elif value == '?maxKey':
                    rep_query[key] = { '$maxKey': 1 }
                elif value == '?array<>':
                    rep_query[key] = ["a", 1]
                elif value == '?array<?number>':
                    rep_query[key] = [1]
                elif value == '?array<?string>':
                    rep_query[key] = ["a"]
                elif value == '?array<?date>':
                    rep_query[key] = [{"$date": "2024-01-01T00:00:00.000Z"}]
                elif value == '?array<?objectId>':
                    rep_query[key] = [{"$oid": "000000000000000000000000"}]
                elif value == '?array<?bool>':
                    rep_query[key] = [True]
                elif value == '?array<?null>':
                    rep_query[key] = [None]
                elif value == '?array<?object>':
                    rep_query[key] = [{"a": 1}]
                elif value == '?array<?binData>':
                    rep_query[key] = [{"$binary": {"base64": "YQ==", "subType": "00"}}]
                elif value == '?array<?timestamp>':
                    rep_query[key] = [{ "$timestamp": { "t": 1677749825, "i": 20 } }]
                elif value == '?array<?minKey>':
                    rep_query[key] = [{ '$minKey': 1 }]
                elif value == '?array<?maxKey>':
                    rep_query[key] = [{ '$maxKey': 1 }]
                elif value == '?array<?array>':
                    rep_query[key] = [["a"]]
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
def transform_to_mongosh(query):
    def transform(value):
        if isinstance(value, dict):
            if '$date' in value:
                return f'ISODate("{value["$date"]}")'
            elif '$oid' in value:
                return f'ObjectId("{value["$oid"]}")'
            elif '$binary' in value:
                return f'BinData({int(value["$binary"]["subType"], 16)}, "{value["$binary"]["base64"]}")'
            elif '$timestamp' in value:
                return f'Timestamp({value["$timestamp"]["t"]}, {value["$timestamp"]["i"]})'
            elif '$minKey' in value:
                return 'MinKey()'
            elif '$maxKey' in value:
                return 'MaxKey()'
            else:
                return {k: transform(v) for k, v in value.items()}
        elif isinstance(value, list):
            return [transform(item) for item in value]
        else:
            return value

    return transform(query)

import json

@safe_execute
def stringify_for_mongosh(obj, indent=0):
    if isinstance(obj, dict):
        if not obj:
            return "{}"
        lines = ["{"]
        for k, v in obj.items():
            lines.append(f"{' ' * (indent + 2)}{stringify_for_mongosh(k)}: {stringify_for_mongosh(v, indent + 2)},")
        lines[-1] = lines[-1].rstrip(',')  # Remove trailing comma from last item
        lines.append(f"{' ' * indent}}}")
        return "\n".join(lines)
    elif isinstance(obj, list):
        if not obj:
            return "[]"
        lines = ["["]
        for item in obj:
            lines.append(f"{' ' * (indent + 2)}{stringify_for_mongosh(item, indent + 2)},")
        lines[-1] = lines[-1].rstrip(',')  # Remove trailing comma from last item
        lines.append(f"{' ' * indent}]")
        return "\n".join(lines)
    elif isinstance(obj, str):
        if obj.startswith('ISODate(') or obj.startswith('ObjectId(') or obj.startswith('BinData(') or \
           obj.startswith('Timestamp(') or obj == 'MinKey()' or obj == 'MaxKey()':
            return obj
        else:
            return json.dumps(obj)
    elif obj is None:
        return 'null'
    elif isinstance(obj, bool):
        return 'true' if obj else 'false'
    else:
        return str(obj)

@safe_execute
def create_rejection_filter(query_shape, rep_query):
    command = query_shape.get('command', 'Unknown')
    db_name = query_shape['cmdNs']['db']
    coll_name = query_shape['cmdNs']['coll']
    sort = query_shape.get('sort')

    rejection_filter = {
        "setQuerySettings": {
            command: coll_name,
            "$db": db_name
        },
        "settings": {
            "reject": True
        }
    }

    # Transform the representative query to mongosh format
    transformed_query = transform_to_mongosh(rep_query)

    # Determine whether to use 'filter' or 'pipeline' based on rep_query type
    if isinstance(transformed_query, list):
        rejection_filter["setQuerySettings"]["pipeline"] = transformed_query
    else:
        rejection_filter["setQuerySettings"]["filter"] = transformed_query

    if sort:
        rejection_filter["setQuerySettings"]["sort"] = sort

    # Convert the rejection filter to a pretty-printed mongosh-compatible string
    return f"db.adminCommand(\n{stringify_for_mongosh(rejection_filter, indent=2)}\n)"

    # Transform the representative query to mongosh format
    transformed_query = transform_to_mongosh(rep_query)

    # Determine whether to use 'filter' or 'pipeline' based on rep_query type
    if isinstance(transformed_query, list):
        rejection_filter["setQuerySettings"]["pipeline"] = transformed_query
    else:
        rejection_filter["setQuerySettings"]["filter"] = transformed_query

    if sort:
        rejection_filter["setQuerySettings"]["sort"] = sort

    # Convert the rejection filter to a mongosh-compatible string
    return f"db.adminCommand({stringify_for_mongosh(rejection_filter)})"

@safe_execute
def extract_index_names(plan):
    index_names = set()
    
    def traverse_plan(node):
        if isinstance(node, dict):
            if 'indexName' in node:
                index_names.add(node['indexName'])
            for value in node.values():
                traverse_plan(value)
        elif isinstance(node, list):
            for item in node:
                traverse_plan(item)
    
    traverse_plan(plan)
    return list(index_names)

@safe_execute
def hash_query_shape(query_shape):
    """
    Create a hash of the query shape, preserving structure and placeholder types.
    """
    def _hash_element(element):
        if isinstance(element, dict):
            return json.dumps({k: _hash_element(v) for k, v in sorted(element.items())}, sort_keys=True)
        elif isinstance(element, list):
            return json.dumps([_hash_element(item) for item in element], sort_keys=True)
        elif isinstance(element, str) and element.startswith('?'):
            return element  # Preserve placeholder types
        else:
            return type(element).__name__  # Use type name for non-placeholder values

    serialized = _hash_element(query_shape)
    return hashlib.sha256(serialized.encode()).hexdigest()

@safe_execute
def correlate_queries(query_stats, query_settings):
    """
    Correlate queries from $queryStats with those in $querySettings using hash-based matching.
    """
    # Hash all queries in $querySettings
    settings_hash_map = {}
    for setting in query_settings:
        debug_shape = setting.get('debugQueryShape', {})
        query_shape = debug_shape.get('filter') or debug_shape.get('pipeline')
        if query_shape:
            query_hash = hash_query_shape(query_shape)
            settings_hash_map[query_hash] = setting

    # Correlate $queryStats queries with $querySettings
    correlated_queries = []
    for stat in query_stats:
        query_shape = stat['key']['queryShape']
        command = query_shape.get('command')
        if command == 'find':
            shape_to_hash = query_shape.get('filter', {})
        elif command == 'aggregate':
            shape_to_hash = query_shape.get('pipeline', [])
        else:
            continue  # Skip unsupported commands

        query_hash = hash_query_shape(shape_to_hash)
        matching_setting = settings_hash_map.get(query_hash)

        correlated_queries.append({
            'query_stat': stat,
            'query_setting': matching_setting
        })

    return correlated_queries