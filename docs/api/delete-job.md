This API call lets you delete an existing job, identified by its ID.

**URL**
```
DELETE /api/job/<JOB_ID>
```

`JOB_ID` must be a valid job `id`.

**Output**

This API call doesn't produce any output.

**Response codes**

| Status Code  | Description |
| ------------ | ------------- |
| 200  | The deletion has been made successfully |
| 404  | **TBD**: The job is unknown |
| 500  | An expected error occurred |

**How to use it**

```
curl -XDELETE http://<CORTEX_SERVER>:<CORTEX_PORT>/api/job/<JOB_ID>
```
