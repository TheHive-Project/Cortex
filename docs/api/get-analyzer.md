This API call returns the details of a given analyzer when you supply its ID. If you don't know the ID of the analyzer, you can get a list of all the available analyzers and the corresponding IDs by referring to the [List analyzers](List-analyzers-API) page.

**URL** 
```
GET /api/analyzer/<ANALYZER_ID>
```

`ANALYZER_ID` should be a valid analyzer `id`.

**Output**

Returns a JSON object representing an analyzer, with the following attributes:

| Attribute  | Type | Description |
| ------------ | ------------- | ------------- |
| id  | String  | The analyzer's identifier  |
| name  | String| The analyzer's name  |
| version  | String  | The analyzer's version  |
| dataTypeList  | String[]  | An array of the observable datatypes that the analyzer can act upon  |

*Example*

```json
{
  "name": "File_Info",
  "version": "1.0",
  "description": "Parse files in several formats such as OLE and OpenXML to detect VBA macros, extract their source code, generate useful information on PE, PDF files and much more.",
  "dataTypeList": [
    "file"
  ],
  "id": "File_Info_1_0"
}
```

**How to use it**

```
curl http://<CORTEX_SERVER>:<CORTEX_PORT>/api/analyzer/<ANALYZER_ID>
```