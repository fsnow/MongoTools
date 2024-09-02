import streamlit as st
from pymongo import MongoClient
import json
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
            st.error(error_msg)
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
        return coll.aggregate(query_shape['pipeline']).explain()
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

@safe_execute
def display_query_stat(client, entry, show_rejection_filter):
    query_shape = entry['key']['queryShape']
    command = query_shape.get('command', 'Unknown')
    
    st.write(f"Command: {command}")
    
    if command == 'find':
        filter_content = query_shape.get('filter', {})
        st.write("Filter:")
        st.json(filter_content)
    elif command == 'aggregate':
        pipeline = query_shape.get('pipeline', [])
        st.write("Pipeline:")
        st.json(pipeline)
    else:
        st.write("Unsupported command type")

    # Display sort information if present
    sort = query_shape.get('sort')
    if sort:
        with st.expander("Sort", expanded=True):
            st.json(sort)

    # Get and display plan stages
    plan = get_explain_plan(client, entry)
    if plan:
        stages = get_plan_stages(plan)
        with st.expander("Plan Stages", expanded=False):
            st.json(stages)
        
        # Suggest index if COLLSCAN is present
        if has_collscan(stages):
            filter_doc = query_shape.get('filter', {}) if command == 'find' else {}
            if command == 'aggregate':
                for stage in query_shape.get('pipeline', []):
                    if '$match' in stage:
                        filter_doc = stage['$match']
                        break
            suggested_index = suggest_index(filter_doc, sort)
            with st.expander("Suggested Index", expanded=True):
                st.json(suggested_index)

    with st.expander("Show client details"):
        st.json(entry['key']['client'])

    with st.expander("Show metrics"):
        st.json(entry['metrics'])

    # Create and display Rejection Filter if MongoDB version is 8+
    if show_rejection_filter and command in ['find', 'aggregate']:
        if command == 'find':
            rep_query = create_representative_query(query_shape.get('filter', {}))
        else:  # aggregate
            rep_query = create_representative_query(query_shape.get('pipeline', []))
        
        rejection_filter = create_rejection_filter(query_shape, rep_query)
        
        with st.expander("Rejection Filter", expanded=False):
            st.code(f"db.adminCommand({json.dumps(rejection_filter, indent=2)})", language="javascript")

    st.markdown("---")  # Add a dividing line

def main():
    st.title("MongoDB $queryStats Analyzer")

    # MongoDB connection
    connection_string = st.text_input("MongoDB Connection String", "mongodb://localhost:27017")
    if st.button("Connect"):
        try:
            client = MongoClient(connection_string)
            st.session_state.client = client
            st.session_state.query_stats = get_query_stats(client)
            st.session_state.mongodb_version = get_mongodb_version(client)
            st.success(f"Connected successfully! MongoDB version: {st.session_state.mongodb_version}")
        except Exception as e:
            error_msg = f"Error connecting to MongoDB: {str(e)}"
            logger.error(error_msg)
            logger.error(traceback.format_exc())
            st.error(error_msg)

    if 'client' in st.session_state:
        try:
            # Create dropdown for namespace selection
            namespaces = list(st.session_state.query_stats.keys())
            namespace_options = [f"{ns} ({len(st.session_state.query_stats[ns])})" for ns in namespaces]
            selected_option = st.selectbox("Select Namespace", namespace_options)
            
            if selected_option:
                selected_namespace = selected_option.split(" (")[0]
                db_name, coll_name = selected_namespace.split('.')
                
                # Determine the number of columns based on MongoDB version
                if st.session_state.mongodb_version >= 8:
                    col1, col2, col3 = st.columns(3)
                else:
                    col1, col2 = st.columns(2)
                
                with col1:
                    st.subheader("$queryStats")
                    for entry in st.session_state.query_stats[selected_namespace]:
                        display_query_stat(st.session_state.client, entry, st.session_state.mongodb_version >= 8)
                
                with col2:
                    st.subheader("Indexes")
                    indexes = get_indexes(st.session_state.client, selected_namespace)
                    for index in indexes:
                        st.json(index)
                
                if st.session_state.mongodb_version >= 8:
                    with col3:
                        st.subheader("$querySettings")
                        debug_shapes = get_debug_query_shapes(st.session_state.client, db_name, coll_name)
                        for shape in debug_shapes:
                            st.json(shape)
        except Exception as e:
            error_msg = f"Error in main loop: {str(e)}"
            logger.error(error_msg)
            logger.error(traceback.format_exc())
            st.error(error_msg)

if __name__ == "__main__":
    main()
