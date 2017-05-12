This API call returns the list of all the analyzers that can act upon a given datatype (IP address, hash, domain...). 

**URL** 
```
GET /api/analyzer/type/<DATATYPE>
```

where `DATATYPE` is a valid observable datatype: ip, url, domain, and so on.

**Output**

Returns a JSON array representing a list of all the analyzers that can act upon that specific datatype. Each entry includes the following attributes:

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| id  | String  | The analyzer's identifier  |
| name  | String| The analyzer's name  |
| version  | String  | The analyzer's version  |
| dataTypeList  | String[]  | An array of the observable datatypes that the analyzer can act upon  |

*Example*

```json
[  
  {
    "name": "Abuse_Finder",
    "version": "1.0",
    "description": "Use CERT-SG's Abuse Finder to find the abuse contact associated with domain names, URLs, IP and email addresses.",
    "dataTypeList": [
      "ip",
      "domain",
      "url",
      "email"
    ],
    "id": "Abuse_Finder_1_0"
  },
  {
    "name": "HippoMore",
    "version": "1.0",
    "description": "Hippocampe detailed report: provides the last detailed report for an IP, domain or a URL",
    "dataTypeList": [
      "ip",
      "domain",
      "fqdn",
      "url"
    ],
    "id": "HippoMore_1_0"
  }
]
```

**How to use it**

```
curl http://<CORTEX_SERVER>:<CORTEX_PORT>/api/analyzer/type/domain
```