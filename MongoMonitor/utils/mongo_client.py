from pymongo import MongoClient as PyMongoClient
from typing import List
import streamlit as st
import certifi

class MongoClient:
    def __init__(self, uri: str):
        try:
            # First try with SSL certificate verification
            self.client = PyMongoClient(
                uri,
                tlsCAFile=certifi.where()
            )
            # Test the connection
            self.client.server_info()
        except Exception as first_error:
            try:
                # If that fails, try without SSL verification (development only)
                st.warning("⚠️ Attempting connection without SSL verification (not recommended for production)")
                self.client = PyMongoClient(
                    uri,
                    tlsAllowInvalidCertificates=True
                )
                # Test the connection
                self.client.server_info()
            except Exception as e:
                raise Exception(f"Failed to connect to MongoDB: {str(e)}")
    
    def list_database_names(self) -> List[str]:
        return self.client.list_database_names()
    
    def __getitem__(self, name: str):
        return self.client[name]