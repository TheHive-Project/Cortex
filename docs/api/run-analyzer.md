The API calls described below will let you run analyzers on one observable at a time. Two types of observables can be analyzed:
- Value-based: generally string values such as IP addresses, domains, hashes and so on.
- File-based: the file that you'd like to analyze must be submitted.

## Analyze Value-based Observables

**URL** 
```
POST /api/analyzer/<ANALYZER_ID>/run
```

`ANALYZER_ID` must be a valid analyzer `id`.

**Input**

This API call requires a JSON POST body describing an observable and the following attributes:

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| data  | String  | The observable's value  |
| attributes  | `<Attributes>`Object | The observable's attributes |


The `<Attributes>` object structure is the following:

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| dataType  | String | The observable's data type |
| tlp  | Number | The observable's TLP: 0 for `WHITE`, 1 for `GREEN`, 2 for `AMBER`, 3 for `RED` |

*Example*

```
curl -XPOST -H 'Content-Type: application/json' http://<CORTEX_SERVER>:<CORTEX_PORT>/api/analyzer/Hipposcore_1_0/run -d '{
    "data":"mydomain.com",
    "attributes":{
        "dataType":"domain",
        "tlp":2
    }
}'
```

This returns the details of the created analysis job.

**Output**

Returns a JSON object representing the started analysis job:

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| id  | String  | The job's ID |
| analyzerId  | String| The analyzer's ID |
| status  | String  | The job's status: `Success`, `InProgress` or `Failure` |
| date  | Number  | A timestamp which represents the job's start date |
| artifact  | Object  | The observable details |

*Example*

```json
{
    "id": "ymlrxZB8efyZhFEg",
    "analyzerId": "Hipposcore_1_0",
    "status": "Success",
    "date": 1490263456480,
    "artifact": {
        "data": "mydomain.com",
        "attributes": {
            "dataType": "domain",
            "tlp": 2
        }
    }
}
```

## Analyze File-based observables

**URL** 
```
POST /api/analyzer/<ANALYZER_ID>/run
```

`ANALYZER_ID` must be a valid analyzer `id`.

**Input**

This API call requires submitting the file to be analyzed by sending a request as a multipart format:

- The first part must be named `data` and must contain the file.
- The second part must be named `_json` and must specify the observable details in JSON format, as described below:

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| dataType  | String | The observable's data type |
| tlp  | Number | The observable's TLP: 0 for `WHITE`, 1 for `GREEN`, 2 for `AMBER`, 3 for `RED` |

*Example*

```
curl -XPOST http://<CORTEX_SERVER>:<CORTEX_PORT>/api/analyzer/File_Info_1_0/run \
    -F '_json={
        "dataType":"file",
        "tlp":2        
    };type=application/json' \
    -F 'attachment=@file.png;type=image/png'
```

This returns the details of the created analysis job.

**Output**

Returns a JSON object representing the started analysis job.

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| id  | String  | The job id |
| analyzerId  | String| The analyzer's id |
| status  | String  | The job's status: `Success`, `InProgress` or `Failure` |
| date  | Number  | A timestamp which represents the job's start date |
| artifact  | Object  | The observable details |

*Example*

```json
{
    "id": "LOcqObDtJEOayPuV",
    "analyzerId": "File_Info_1_0",
    "status": "Success",
    "date": 1490265356725,
    "artifact": {
        "attributes": {
            "dataType": "file",
            "tlp": 2,
            "content-type": "image/png",
            "filename": "file.png"
        }
    }
}
```
