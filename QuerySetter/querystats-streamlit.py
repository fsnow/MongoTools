import streamlit as st
import json
import extra_streamlit_components as stx
from pymongo import MongoClient
from mongodb_functions import *

# Function to load and apply custom CSS
def load_css():
    with open("style.css", "r") as f:
        st.markdown(f"<style>{f.read()}</style>", unsafe_allow_html=True)

# Set page config to wide mode and load CSS
st.set_page_config(layout="wide")
load_css()

# Initialize cookie manager
cookie_manager = stx.CookieManager()

@safe_execute
def display_query_stat(client, entry, matching_setting, show_rejection_filter):
    query_shape = entry['key']['queryShape']
    command = query_shape.get('command', 'Unknown')
    
    col1, col2 = st.columns(2)
    
    with col1:
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
            st.write("Sort:")
            st.json(sort)

    with col2:
        # Get and display plan stages
        plan = get_explain_plan(client, entry)
        if plan:
            stages = get_plan_stages(plan)
            with st.expander("Plan Stages", expanded=False):
                st.json(stages)
            
            # Display Winning Plan
            winning_plan = plan.get('queryPlanner', {}).get('winningPlan', {})
            with st.expander("Winning Plan", expanded=False):
                st.json(winning_plan)
            
            # Extract and display Indexes Used
            indexes_used = extract_index_names(winning_plan)
            if indexes_used:
                with st.expander("Indexes Used", expanded=False):
                    for index_name in indexes_used:
                        index_info = get_index_info(client, query_shape['cmdNs']['db'], 
                                                    query_shape['cmdNs']['coll'], index_name)
                        if index_info:
                            st.json(index_info)
                        else:
                            st.write(f"Index information not found for: {index_name}")
            
            # Suggest index if COLLSCAN is present
            if has_collscan(stages):
                filter_doc = query_shape.get('filter', {}) if command == 'find' else {}
                if command == 'aggregate':
                    for stage in query_shape.get('pipeline', []):
                        if '$match' in stage:
                            filter_doc = stage['$match']
                            break
                suggested_index = suggest_index(filter_doc, sort)
                with st.expander("Suggested Index", expanded=False):
                    st.json(suggested_index)

        with st.expander("Client Details", expanded=False):
            st.json(entry['key']['client'])

        with st.expander("Metrics", expanded=False):
            st.json(entry['metrics'])

        # Display matching Query Settings if available
        if matching_setting:
            with st.expander("Query Settings", expanded=False):
                st.json(matching_setting)

        # Create and display Rejection Filter if MongoDB version is 8+ and not already rejected
        if (show_rejection_filter and command in ['find', 'aggregate'] and 
            not (matching_setting and matching_setting.get('settings', {}).get('reject', False))):
            if command == 'find':
                rep_query = create_representative_query(query_shape.get('filter', {}))
            else:  # aggregate
                rep_query = create_representative_query(query_shape.get('pipeline', []))
            
            rejection_filter = create_rejection_filter(query_shape, rep_query)
            
            with st.expander("Rejection Filter Mongosh Command", expanded=False):
                st.code(rejection_filter, language="javascript")

    st.markdown("---")  # Add a dividing line

def connection_dialog():
    with st.sidebar:
        st.subheader("MongoDB Connection")
        stored_connection_string = cookie_manager.get(cookie='connection_string')
        connection_string = st.text_input("Connection String", 
                                          value=stored_connection_string or "mongodb://localhost:27017",
                                          type="password")
        
        if st.button("Connect"):
            try:
                client = MongoClient(connection_string)
                st.session_state.client = client
                st.session_state.mongodb_version = get_mongodb_version(client)
                st.session_state.connected = True
                st.session_state.connection_error = None
                
                # Store the connection string in a cookie
                cookie_manager.set('connection_string', connection_string, expires_at=None)
                
                # Get query stats
                query_stats_result = get_query_stats(client)
                
                if isinstance(query_stats_result, dict) and "error" in query_stats_result:
                    st.session_state.query_stats_error = query_stats_result["error"]
                    st.session_state.query_stats = None
                    if query_stats_result["error"] == "rate_limit_zero":
                        st.session_state.rate_limit = 0
                else:
                    st.session_state.query_stats = query_stats_result
                    st.session_state.query_stats_error = None
                    st.session_state.rate_limit = None  # Reset rate_limit if query stats are successfully retrieved
                
            except Exception as e:
                st.session_state.connected = False
                st.session_state.connection_error = str(e)

        # Display connection status and errors
        if st.session_state.get('connected', False):
            st.success(f"Connected! MongoDB version: {st.session_state.mongodb_version}")
        if st.session_state.get('connection_error'):
            st.error(f"Connection error: {st.session_state.connection_error}")

def main():
    st.title("MongoDB $queryStats Analyzer")

    # Initialize session state variables
    if 'connected' not in st.session_state:
        st.session_state.connected = False
    if 'connection_error' not in st.session_state:
        st.session_state.connection_error = None
    if 'query_stats_error' not in st.session_state:
        st.session_state.query_stats_error = None
    if 'rate_limit' not in st.session_state:
        st.session_state.rate_limit = None

    # Display connection dialog
    connection_dialog()

    if st.session_state.rate_limit == 0:
        st.warning("⚠️ The internalQueryStatsRateLimit is set to 0. $queryStats sampling is disabled.")
        
        # Add text input and button to set new rate limit
        col1, col2 = st.columns([3, 1])
        with col1:
            new_rate_limit = st.number_input("Enter new internalQueryStatsRateLimit:", min_value=1, value=100, step=1)
        with col2:
            if st.button("Set Rate Limit"):
                try:
                    st.session_state.client.admin.command({'setParameter': 1, 'internalQueryStatsRateLimit': new_rate_limit})
                    st.success(f"internalQueryStatsRateLimit set to {new_rate_limit}")
                    # Refresh query stats
                    query_stats_result = get_query_stats(st.session_state.client)
                    if isinstance(query_stats_result, dict) and "error" in query_stats_result:
                        st.session_state.query_stats_error = query_stats_result["error"]
                        st.session_state.query_stats = None
                    else:
                        st.session_state.query_stats = query_stats_result
                        st.session_state.query_stats_error = None
                        st.session_state.rate_limit = None
                except Exception as e:
                    st.error(f"Error setting rate limit: {str(e)}")
        
        st.info("To enable $queryStats, set internalQueryStatsRateLimit to a positive integer.")
    elif st.session_state.query_stats_error == "empty_query_stats":
        st.info("No query statistics available. This could be because no queries have been executed since the last server restart or $queryStats collection.")

    if st.session_state.connected and st.session_state.query_stats and not st.session_state.query_stats_error:
        try:
            # Create dropdown for namespace selection
            namespaces = list(st.session_state.query_stats.keys())
            namespace_options = [f"{ns} ({len(st.session_state.query_stats[ns])})" for ns in namespaces]
            selected_option = st.selectbox("Select Namespace", namespace_options)
            
            if selected_option:
                selected_namespace = selected_option.split(" (")[0]
                db_name, coll_name = selected_namespace.split('.')
                
                # Create tabs for different views
                tab1, tab2, tab3 = st.tabs(["$queryStats", "Indexes", "$querySettings"])
                
                with tab1:
                    st.subheader("$queryStats")
                    debug_shapes = get_debug_query_shapes(st.session_state.client, db_name, coll_name)
                    correlated_queries = correlate_queries(st.session_state.query_stats[selected_namespace], debug_shapes)
                    
                    for correlated_query in correlated_queries:
                        display_query_stat(st.session_state.client, correlated_query['query_stat'], 
                                           correlated_query['query_setting'], 
                                           st.session_state.mongodb_version >= 8)
                
                with tab2:
                    st.subheader("Indexes")
                    indexes = get_indexes(st.session_state.client, selected_namespace)
                    for index in indexes:
                        st.json(index)
                
                with tab3:
                    st.subheader("$querySettings")
                    if st.session_state.mongodb_version >= 8:
                        debug_shapes = get_debug_query_shapes(st.session_state.client, db_name, coll_name)
                        for shape in debug_shapes:
                            st.json(shape)
                    else:
                        st.info("$querySettings is only available in MongoDB 8.0 and above.")
        except Exception as e:
            st.error(f"Error in main loop: {str(e)}")

if __name__ == "__main__":
    main()