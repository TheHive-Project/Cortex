
If you are using [TheHive](https://github.com/CERT-BDF/TheHive) as a SIRP (Security Incident Response Platform), you
don't need to master the Cortex REST API. If you have a different SIRP or would like to interface other tools with
Cortex, please read on.

## TL;DR
The current Cortex version doesn't require authentication and all API call results are provided in JSON format.

## Available API Calls

- [List analyzers](list-analyzers.md)
- [Get an analyzer's definition](get-analyzer.md)
- [List analyzers for a given datatype](get-analyzer-by-type.md)
- [Run an analyzer](run-analyzer.md)
- [List jobs](list-jobs.md)
- [Get a job definition](get-job.md)
- [Delete a job](delete-job.md)
- [Get a job report](get-job-report.md)
- [Wait and get a job report](wait-and-get-job-report.md)

## How to create an analyzer

If you want to create an analyzer, follow this [guide](how-to-create-an-analyzer.md).