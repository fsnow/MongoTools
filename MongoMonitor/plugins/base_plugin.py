from abc import ABC, abstractmethod
from utils.mongo_client import MongoClient

class BasePlugin(ABC):
    name = "Base Plugin"
    
    def __init__(self, mongo_client: MongoClient):
        self.mongo_client = mongo_client
    
    @abstractmethod
    def render(self):
        """Implement the plugin's Streamlit UI and functionality."""
        pass