# Atlas Data Federation
## Change Cluster and Use Wildcard Databases and Collections

This [script](adf-change-cluster.py) reconfigures an existing Atlas Data Federation instance to use a different cluster as its data source. It also changes the data source mapping to use wildcards so that all databases and collections in the cluster are available without any additional configuration.

Usage:
```
python adf-change-cluster.py <PROJECT_ID> <FEDERATED_NAME> <CLUSTER_NAME> --public_key <API_PUBLIC_KEY> --private_key API_PRIVATE_KEY
```

The Atlas API key will be read from the environment variables MONGODB_ATLAS_PUBLIC_KEY and MONGODB_ATLAS_PRIVATE_KEY if not passed on the command line.

## How it Works

The script first calls the [getFederatedDatabase](https://www.mongodb.com/docs/atlas/reference/api-resources-spec/v2/#tag/Data-Federation/operation/getFederatedDatabase) API to get the full configuration JSON for the instance. A new JSON is contructed from the GET response with the top-level fields cloudProviderConfig, dataProcessRegion and name. To this we add the storage field that has the wildcard database configuration as well as the new cluster name. We then call the [updateFederatedDatabase](https://www.mongodb.com/docs/atlas/reference/api-resources-spec/v2/#tag/Data-Federation/operation/updateFederatedDatabase) API to modify the configuration.

Note that the hard-coded configuration also includes secondary read preference, so modify this if desired.

In my testing I found that if the region of the federated database instance is not specified in the configuration (i.e. set to "closest"), the get API returns {region: null}, but the update API fails with this value. I had to change from "closest" to a specified region.

