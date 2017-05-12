This API call returns the details of a given job, identified by its ID. It doesn't include the job's report, which can be fetched using the [Get job report API](Get-job-report-API).

**URL** 
```
GET /api/job/<JOB_ID>
```

`JOB_ID` must be a valid job `id`.

**Output**

Returns a JSON object representing a job with the following attributes:

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| id  | String  | The job's id |
| analyzerId  | String| The analyzer's id |
| status  | String  | The job's status: `Success`, `InProgress` or `Failure` |
| date  | Number  | A timestamp which represents the job's start date |
| artifact  | Object  | The observable details |

*Example*

```json
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
}
```

**How to use it**

```
curl http://<CORTEX_SERVER>:<CORTEX_PORT>/api/job/<JOB_ID>
```