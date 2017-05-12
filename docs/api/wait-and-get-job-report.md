This API call is almost the same as [Get Job Report API](Get-job-report-API) but introduces an asynchronous behavior. It means that this API can wait for a given amount of time until the job completes. It also supports a timeout parameter.

Instead of returning the details of an in-progress job, it will wait until it finishes then returns the report, provided it doesn't timeout.

**URL** 
```
GET /api/job/<JOB_ID>/waitreport?atMost=<DURATION>
```

`JOB_ID` must be a valid job `id`.
`DURATION` should be a valid duration, default to infinite. The duration format is a string composed by a number and a unit (based on [Scala durations](http://www.scala-lang.org/api/2.9.3/scala/concurrent/duration/Duration.html)), for example:
- 10seconds
- 1minute
- 10minutes
- 2hours

If `atMost`query parameter is not specified, it defaults to *Infinite* (which can be a very, very long time considering the age of the Universe).

**Output**

Same output as [Get Job Report API](Get-job-report-API).

**How to use it**

```
curl http://<CORTEX_SERVER>:<CORTEX_PORT>/api/job/<JOB_ID>/waitreport
```