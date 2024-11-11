import re
import sys
import json

def parse_shard_distribution(raw_text):
    """
    Parse MongoDB's getShardDistribution() output into a dictionary.
    Stops parsing at the "Totals" line.
    Separates data size units into a separate field.
    
    Args:
        raw_text (str): Raw output from getShardDistribution()
        
    Returns:
        dict: Dictionary with shard names as keys and their stats as values
    """
    # Stop at "Totals" line
    content = raw_text.split('\nTotals')[0]
    
    # Split the input into sections (one per shard)
    sections = re.split(r'\nShard ', content.strip())
    if sections[0].startswith('Shard '):
        sections[0] = sections[0][6:]  # Remove 'Shard ' from first section
    
    result = {}
    
    for section in sections:
        # Parse each section
        lines = section.strip().split('\n')
        
        # Extract shard name from the first line
        shard_name = lines[0].split(' at ')[0]
        
        # Parse the JSON-like data
        data_lines = '\n'.join(lines[1:])  # Join remaining lines
        # Remove curly braces
        data_lines = data_lines.strip('{}')
        
        # Parse key-value pairs
        pairs = {}
        for line in data_lines.split(',\n'):
            line = line.strip()
            if not line:
                continue
            key, value = line.split(': ', 1)
            # Clean up the key and value
            key = key.strip("'")
            value = value.strip("'")
            
            # Special handling for data field to separate units
            if key == 'data':
                match = re.match(r'([\d.]+)(\w+)', value)
                if match:
                    pairs['data'] = match.group(1)
                    pairs['dataUnits'] = match.group(2)
                continue
            
            # Convert numeric values where appropriate
            if 'docs' == key or 'chunks' == key:
                value = int(value)
            
            pairs[key] = value
            
        result[shard_name] = pairs
    
    return result

if __name__ == "__main__":
    # Read all input from STDIN
    input_data = sys.stdin.read()
    
    # Parse the data
    result = parse_shard_distribution(input_data)
    
    # Print the result as JSON
    print(json.dumps(result, indent=2))