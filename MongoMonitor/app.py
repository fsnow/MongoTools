import streamlit as st
import importlib
import pkgutil
import os
from typing import Dict, Type
import plugins
from plugins.base_plugin import BasePlugin
from utils.mongo_client import MongoClient

class MongoDBMonitor:
    def __init__(self):
        self.plugins: Dict[str, Type[BasePlugin]] = {}
        self.load_plugins()
        
        # Initialize session state
        if 'mongo_client' not in st.session_state:
            st.session_state.mongo_client = None

    def load_plugins(self):
        """Dynamically load all plugins from the plugins directory."""
        plugin_package = plugins
        for _, name, _ in pkgutil.iter_modules(plugin_package.__path__):
            if name != 'base_plugin':
                module = importlib.import_module(f'plugins.{name}')
                for item_name in dir(module):
                    item = getattr(module, item_name)
                    try:
                        if (issubclass(item, BasePlugin) and 
                            item != BasePlugin):
                            self.plugins[item.name] = item
                    except TypeError:
                        continue

    def run(self):
        st.title("MongoDB Monitor")
        
        # Connection settings
        with st.sidebar:
            st.header("MongoDB Connection")
            uri = st.text_input("MongoDB URI", "mongodb://localhost:27017")
            
            if st.button("Connect"):
                try:
                    st.session_state.mongo_client = MongoClient(uri)
                    st.success("Connected to MongoDB!")
                except Exception as e:
                    st.error(f"Failed to connect: {str(e)}")
                    st.session_state.mongo_client = None

            # Add disconnect button
            if st.session_state.mongo_client is not None:
                if st.button("Disconnect"):
                    st.session_state.mongo_client = None
                    st.success("Disconnected from MongoDB!")

        if st.session_state.mongo_client is None:
            st.warning("Please connect to MongoDB first")
            return

        # Plugin selection
        selected_plugin = st.sidebar.selectbox(
            "Select Function",
            options=list(self.plugins.keys())
        )

        # Initialize and run selected plugin
        if selected_plugin:
            plugin = self.plugins[selected_plugin](st.session_state.mongo_client)
            plugin.render()

if __name__ == "__main__":
    app = MongoDBMonitor()
    app.run()