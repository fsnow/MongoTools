import streamlit as st
import pandas as pd
from plugins.base_plugin import BasePlugin

class IndexAnalyzer(BasePlugin):
    name = "Index Analyzer"
    
    def render(self):
        st.header(self.name)
        
        try:
            # Get database names
            databases = self.mongo_client.list_database_names()
            selected_db = st.selectbox("Select Database", databases)
            
            if selected_db:
                db = self.mongo_client[selected_db]
                collections = db.list_collection_names()
                selected_collection = st.selectbox(
                    "Select Collection", 
                    collections
                )
                
                if selected_collection:
                    collection = db[selected_collection]
                    indexes = list(collection.list_indexes())
                    
                    # Convert indexes to DataFrame for better visualization
                    index_data = []
                    for idx in indexes:
                        index_data.append({
                            'name': idx['name'],
                            'keys': str(idx['key']),
                            'unique': idx.get('unique', False),
                            'sparse': idx.get('sparse', False)
                        })
                    
                    if index_data:
                        df = pd.DataFrame(index_data)
                        st.dataframe(df)
                    else:
                        st.info("No indexes found for this collection")
        except Exception as e:
            st.error(f"Error analyzing indexes: {str(e)}")
