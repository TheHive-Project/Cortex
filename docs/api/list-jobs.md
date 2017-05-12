This API call returns a list of analysis jobs.

**URL** 
```
GET /api/job
```

**Input**

This API call supports a list of filters and pagination parameters can be provided in the query:

| Query Parameter  | Default value | Description |
| ------------ | ------------- | ------------- |
| dataTypeFilter  | Empty  | A datatype value: ip, domain, hash etc...  |
| dataFilter  | Empty | A string representing a part of an observable value. Could be an IP or part of an IP, a domain, url and so on |
| analyzerFilter  | Empty | An analyzer's ID  |
| start  | 0  | A number representing the index of the page start |
| limit  | 10  | A number representing a page size |

*Example*
```
GET /api/job?analyzerFilter=Abuse_Finder_1_0&dataTypeFilter=domain&dataFilter=.com&start=0&limit=50
```

should return the list of Abuse_Finder jobs corresponding to domains which include `.com`.

**Output**

Returns a JSON array representing a list of jobs. Each entry includes the following attributes:

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| id  | String  | The job's id |
| analyzerId  | String| The analyzer's id |
| status  | String  | The job's status: `Success`, `InProgress` or `Failure` |
| date  | Number  | A timestamp which corresponds to the job's start date |
| artifact  | Object  | The observable details |

*Example*

```json
[
  {
    "id": "OsmbnQJGmeCgvDxP",
    "analyzerId": "OTXQuery_1_0",
    "status": "Failure",
    "date": 1490194495264,
    "artifact": {
      "data": "8.8.8.8",
      "attributes": {
        "dataType": "ip",
        "tlp": 2
      }
    }
  },
  {
    "id": "c9uZDbHBf32DdIVJ",
    "analyzerId": "MaxMind_GeoIP_2_0",
    "status": "Success",
    "date": 1490194495262,
    "artifact": {
      "data": "8.8.8.8",
      "attributes": {
        "dataType": "ip",
        "tlp": 2
      }
    }
  },
  {
    "id": "OcFlZbLNNUsIiJZq",
    "analyzerId": "HippoMore_1_0",
    "status": "InProgress",
    "date": 1490194495259,
    "artifact": {
      "data": "8.8.8.8",
      "attributes": {
        "dataType": "ip",
        "tlp": 2
      }
    }
  }
]
```

**How to use it**

```
curl http://<CORTEX_SERVER>:<CORTEX_PORT>/api/job
```

or

```
curl 'http://<CORTEX_SERVER>:<CORTEX_PORT>/api/job?start=0&limit=100'
```