This API call returns the list of all the analyzers enabled within Cortex. 

**URL** 
```
GET /api/analyzer
```

**Output**

Returns a JSON array representing a list of all the enabled analyzers. Each entry includes the following attributes:

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
    "name": "File_Info",
    "version": "1.0",
    "description": "Parse files in several formats such as OLE and OpenXML to detect VBA macros, extract their source code, generate useful information on PE, PDF files and much more.",
    "dataTypeList": [
      "file"
    ],
    "id": "File_Info_1_0"
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
curl http://<CORTEX_SERVER>:<CORTEX_PORT>/api/analyzer
```