import os

from dotenv import load_dotenv
from pymongo import MongoClient

load_dotenv()

MONGODB_URL = os.getenv("MONGODB_URL")
DATABASE_NAME = os.getenv("DATABASE_NAME")

client = MongoClient(MONGODB_URL)

db = client[DATABASE_NAME]

users_collection = db["users"]
profiles_collection = db["profiles"]
jobs_collection = db["jobs"]
applications_collection = db["applications"]
notifications_collection = db["notifications"]
