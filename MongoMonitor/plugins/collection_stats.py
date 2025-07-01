import streamlit as st
from plugins.base_plugin import BasePlugin

class CollectionStats(BasePlugin):
    name = "Collection Statistics"
    
    def render(self):
        st.header(self.name)
        
        try:
            # Get database names
            databases = self.mongo_client.list_database_names()
            selected_db = st.selectbox("Select Database", databases)
            
            if selected_db:
                db = self.mongo_client[selected_db]
                collections = db.list_collection_names()
                
                for collection in collections:
                    with st.expander(f"Collection: {collection}"):
                        stats = db.command("collstats", collection)
                        st.json(stats)
        except Exception as e:
            st.error(f"Error fetching collection stats: {str(e)}")
