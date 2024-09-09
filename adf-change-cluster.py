import requests
import json
import os
import sys
import argparse
from dotenv import load_dotenv
from requests.auth import HTTPDigestAuth

# Load environment variables from .env file
load_dotenv()

def get_data_federation_instance_details(public_key, private_key, project_id, tenant_name, cluster_name):
    # Atlas API endpoint (v2)
    url = f"https://cloud.mongodb.com/api/atlas/v2/groups/{project_id}/dataFederation/{tenant_name}"

    # Set up the request headers with the correct Content-Type
    headers = {
        "Accept": "application/vnd.atlas.2024-08-05+json"
    }

    # Set up digest authentication
    auth = HTTPDigestAuth(public_key, private_key)

    try:
        # Make the API request
        response = requests.get(url, headers=headers, auth=auth)

        # Check if the request was successful
        if response.status_code == 200:
            # Parse the JSON response
            federation_details = response.json()
            
            # Filter the JSON to include only the specified fields
            filtered_details = {
                key: federation_details[key]
                for key in ['cloudProviderConfig', 'dataProcessRegion', 'name']
                if key in federation_details
            }
            
            # Add the new storage configuration
            filtered_details['storage'] = {
                "databases": [
                    {
                        "collections": [
                            {
                                "dataSources": [
                                    {
                                        "storeName": cluster_name
                                    }
                                ],
                                "name": "*"
                            }
                        ],
                        "name": "*",
                        "views": []
                    }
                ],
                "stores": [
                    {
                        "provider": "atlas",
                        "clusterName": cluster_name,
                        "name": cluster_name,
                        "projectId": project_id,
                        "readPreference": {
                            "mode": "secondary",
                            "tagSets": []
                        }
                    }
                ]
            }
            
            return filtered_details
        else:
            print(f"Error: {response.status_code} - {response.text}")
            return None

    except requests.exceptions.RequestException as e:
        print(f"An error occurred: {e}")
        return None

def update_data_federation_instance(public_key, private_key, project_id, tenant_name, updated_config):
    # Atlas API endpoint (v2)
    url = f"https://cloud.mongodb.com/api/atlas/v2/groups/{project_id}/dataFederation/{tenant_name}"

    # Set up the request headers
    headers = {
        "Accept": "application/vnd.atlas.2024-08-05+json",
        "Content-Type": "application/json"
    }

    # Set up digest authentication
    auth = HTTPDigestAuth(public_key, private_key)

    try:
        # Make the PATCH API request
        response = requests.patch(url, headers=headers, auth=auth, json=updated_config)

        # Check if the request was successful
        if response.status_code == 200:
            return response.json()
        else:
            print(f"Error: {response.status_code} - {response.text}")
            return None

    except requests.exceptions.RequestException as e:
        print(f"An error occurred: {e}")
        return None

def main():
    # Set up argument parser
    parser = argparse.ArgumentParser(description="Modify MongoDB Atlas Data Federation instance.")
    parser.add_argument("project_id", help="MongoDB Atlas Project ID")
    parser.add_argument("tenant_name", help="MongoDB Atlas Data Federation Tenant Name")
    parser.add_argument("cluster_name", help="MongoDB Atlas Cluster Name")
    parser.add_argument("--public_key", help="MongoDB Atlas Public API Key (overrides environment variable)")
    parser.add_argument("--private_key", help="MongoDB Atlas Private API Key (overrides environment variable)")
    args = parser.parse_args()

    # Read API keys from environment variables or command line arguments
    public_key = args.public_key or os.getenv('MONGODB_ATLAS_PUBLIC_KEY')
    private_key = args.private_key or os.getenv('MONGODB_ATLAS_PRIVATE_KEY')

    # Check if the keys are available
    if not public_key or not private_key:
        print("Error: MongoDB Atlas API keys not found.")
        print("Please provide them either as environment variables (MONGODB_ATLAS_PUBLIC_KEY and MONGODB_ATLAS_PRIVATE_KEY)")
        print("or as command line arguments (--public_key and --private_key).")
        sys.exit(1)

    # Get the current configuration
    current_config = get_data_federation_instance_details(public_key, private_key, args.project_id, args.tenant_name, args.cluster_name)

    if current_config:
        # Update the Data Federation instance
        result = update_data_federation_instance(public_key, private_key, args.project_id, args.tenant_name, current_config)

        if result:
            print("Data Federation instance updated successfully:")
            print(json.dumps(result, indent=2))
        else:
            print("Failed to update Data Federation instance.")
    else:
        print("Failed to retrieve current Data Federation instance details.")

if __name__ == "__main__":
    main()