# QuerySetter

A Streamlit application that demonstrates MongoDB's `$queryStats`, setQuerySettings and `$querySettings`. This tool lets developers examine query shapes through `$queryStats` and view their query plans and execution statistics, and use setQuerySettings/`$querySettings` in MongoDB 8.0 to implement shape-specific settings, including blocking shapes if inefficient or unindexed.

The application generates index suggestions for collection scans. This is not a complete implementation of index recommendations. Notably it does not give correct/complete recommendations when using the $or operator. 

## Prerequisites

- Python 3.6+
- MongoDB 6.0+, 7.0+, 8.0 for $queryStats. Specific minor versions are required. MongoDB 8.0+ required for `$querySettings` features.
- PyMongo
- Streamlit

## Installation

```bash
git clone https://github.com/yourusername/mongodb-query-stats-analyzer.git
cd mongodb-query-stats-analyzer
pip install -r requirements.txt
```

## Usage

Start the application:

```bash
streamlit run querystats-streamlit.py
```

Connect to your MongoDB instance via the sidebar connection string input. Select a namespace from the dropdown to view its query statistics.

## Implementation Details

### mongodb_functions.py

Handles MongoDB operations including:
- Connection management
- Query plan analysis
- Index suggestion generation
- Query shape correlation between `$queryStats` and `$querySettings`

Much of the implementation involves the translation from a query shape to what I am calling a "representative query". The queyr shape has data type placeholders. The representative query substitutes actual values of the correct type for those type placeholders. The setQuerySettings admin command takes an example query, not a query shape.

### querystats-streamlit.py

Implements the web interface for:
- Query visualization
- Namespace selection
- Performance data presentation

## Configuration

The `internalQueryStatsRateLimit` parameter controls the sampling rate of query statistics. If set to 0, the application provides an interface to modify it to enable the Query Stats Store. The recommended value is 100.

## Common Issues

### Connection Problems
- Verify MongoDB server status
- Check network connectivity
- Confirm authentication credentials

### Missing Query Stats
- Check if `internalQueryStatsRateLimit` > 0
- Wait for new queries to execute
- Verify server version compatibility
- Ensure that user has the [queryStatsRead privilege action](https://www.mongodb.com/docs/manual/reference/operator/aggregation/queryStats/#access-control)

### Missing QuerySettings
- Check MongoDB version for 8.0
- Verify user permissions (TODO: what permissions are required for $querySettings / setQuerySettings?)

## License

MIT License