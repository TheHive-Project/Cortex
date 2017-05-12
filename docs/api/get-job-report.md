This API call returns the details and report of a given job, identified by its ID.

**URL** 
```
GET /api/job/<JOB_ID>/report
```

`JOB_ID` must be a valid job `id`.

**Output**

Returns a JSON object representing a job, with the following attributes:

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| id  | String  | The job's id |
| analyzerId  | String| The analyzer's id |
| status  | String  | The job's status: `Success`, `InProgress` or `Failure` |
| date  | Number  | A timestamp which represent the job's start date |
| artifact  | Object  | The observable details |
| report  | `<Report>` Object  | The job report |


The `<Report>` could be any JSON object, but Cortex uses some conventions. The structure of the `<Report>` object as defined by Cortex is described below:

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| success  | Boolean  | True if the job is successful, False if it failed |
| errorMessage | String | Contains the error message if the job failed |
| summary | Object  | A custom JSON object with any content (based on the analyzer) |
| artifacts | `<Artifact>`[]  | An array of the artifacts extracted from the analysis |
| full | Object  | A custom JSON object with any content (based on the analyzer). Represents the full analysis report |


The `<Artifact>` is an object representing an observable and has two attributes:

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| type  | String  | The artifact's datatype (url, hash, ip, domain...) |
| value | String | The observable's value |


*Example*

```json
{
  "id": "vVQu93ps4PwHOtLv",
  "analyzerId": "File_Info_1_0",
  "status": "Success",
  "date": 1490204071457,
  "artifact": {
    "attributes": {
      "dataType": "file",
      "tlp": 2,
      "content-type": "text/x-python-script",
      "filename": "sample.py"
    }
  },
  "report": {
    "artifacts": [
      {
        "type": "sha1",
        "value": "cd1c2da4de388a4b5b60601f8b339518fe8fbd31"
      },
      {
        "type": "sha256",
        "value": "fd1755c7f1f0f85597cf2a1f13f5cbe0782d9e5597aca410da0d5f26cda26b97"
      },
      {
        "type": "md5",
        "value": "3aa598d1f0d50228d48fe3a792071dde"
      }
    ],
    "full": {
      "Mimetype": "text/x-python",
      "Identification": {
        "ssdeep": "24:8ca1hbLcd8yutXHbLcTtvbrbLcvtEbLcWmtlbLca66/5:8zHbLcdbOXbLc5jrbLcVEbLcPlbLcax",
        "SHA1": "cd1c2da4de388a4b5b60601f8b339518fe8fbd31",
        "SHA256": "fd1755c7f1f0f85597cf2a1f13f5cbe0782d9e5597aca410da0d5f26cda26b97",
        "MD5": "3aa598d1f0d50228d48fe3a792071dde"
      },
      "filetype": "python script",
      "Magic": "Python script, ASCII text executable",
      "Exif": {
        "ExifTool:ExifToolVersion": 10.36
      }
    },
    "success": true,
    "summary": {
      "filetype": "python script"
    }
  }
}
```

**How to use it**

```
curl http://<CORTEX_SERVER>:<CORTEX_PORT>/api/job/<JOB_ID>/report
```