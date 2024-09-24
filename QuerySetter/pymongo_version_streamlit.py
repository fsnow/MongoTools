import streamlit as st
import pymongo
import sys

st.title("Python and PyMongo Versions")

st.write(f"The installed Python version is: {sys.version}")
st.write(f"The installed PyMongo version is: {pymongo.__version__}")