# Change Log

## [3.1.7](https://github.com/TheHive-Project/Cortex/milestone/34) (2022-10-07)

**Implemented enhancements:**

- Prevent invalid analyzer when a new version is available [\#426](https://github.com/TheHive-Project/Cortex/issues/426)
- Display job parameters in the report page [\#430](https://github.com/TheHive-Project/Cortex/issues/430)
- An error in docker pull should not stop the analysis [\#431](https://github.com/TheHive-Project/Cortex/issues/431)
- Improve catalog parsing [\#432](https://github.com/TheHive-Project/Cortex/issues/432)

**Closed issues:**

- [BUG] CA Certs parameter can't be set back to null [\#377](https://github.com/TheHive-Project/Cortex/issues/377)
- [FR] See user and organisation who triggered a responder in Cortex WebUI [\#394](https://github.com/TheHive-Project/Cortex/issues/394)

## [3.1.6](https://github.com/TheHive-Project/Cortex/milestone/32) (2022-06-22)

**Fixed bugs:**

- Web frontend doesn't work [\#419](https://github.com/TheHive-Project/Cortex/issues/419)

## [3.1.5](https://github.com/TheHive-Project/Cortex/milestone/29) (2022-06-22)

**Implemented enhancements:**

- Improve logs for troubleshooting [\#412](https://github.com/TheHive-Project/Cortex/issues/412)
- Add API to check status of several jobs [\#417](https://github.com/TheHive-Project/Cortex/issues/417)

**Fixed bugs:**

- Job timeout doesn't work if threadpool is full [\#410](https://github.com/TheHive-Project/Cortex/issues/410)
- Update libraries [\#416](https://github.com/TheHive-Project/Cortex/issues/416)

**Closed issues:**

- Add missing dependencies on Docker image [\#413](https://github.com/TheHive-Project/Cortex/issues/413)
- [Bug ] Authentication Bypass Vulnerability [\#418](https://github.com/TheHive-Project/Cortex/issues/418)

## [3.1.4 - Update library log4j-to-slf4j to version 2.17.0](https://github.com/TheHive-Project/Cortex/milestone/33) (2022-05-24)



## [3.1.3](https://github.com/TheHive-Project/Cortex/milestone/31) (2021-11-10)

**Fixed bugs:**

- The build of frontend fails [\#389](https://github.com/TheHive-Project/Cortex/issues/389)

## [3.1.2](https://github.com/TheHive-Project/Cortex/milestone/30) (2021-11-05)

**Implemented enhancements:**

- Create a docker image with all dependencies [\#388](https://github.com/TheHive-Project/Cortex/issues/388)

**Closed issues:**

- More settings on docker containers instantiated by Cortex [\#387](https://github.com/TheHive-Project/Cortex/issues/387)

## [3.1.1](https://github.com/TheHive-Project/Cortex/milestone/28) (2021-03-01)

**Implemented enhancements:**

- [Improvement] Create logfile after installation [\#341](https://github.com/TheHive-Project/Cortex/issues/341)

**Fixed bugs:**

- [BUG] Certificate not taken into account when running neurons with process [\#317](https://github.com/TheHive-Project/Cortex/issues/317)
- [Bug] Update doesn't work on Elasticsearch 7.11 [\#346](https://github.com/TheHive-Project/Cortex/issues/346)

## [3.1.0](https://github.com/TheHive-Project/Cortex/milestone/27) (2020-10-30)

**Implemented enhancements:**

- Improve Docker image [\#296](https://github.com/TheHive-Project/Cortex/issues/296)
- Impossible to load catalog through a proxy [\#297](https://github.com/TheHive-Project/Cortex/issues/297)
- Update login page design [\#303](https://github.com/TheHive-Project/Cortex/issues/303)

**Fixed bugs:**

- [Bug] Cortex and boolean ConfigurationItems  [\#309](https://github.com/TheHive-Project/Cortex/issues/309)

## [3.1.0-RC1](https://github.com/TheHive-Project/Cortex/milestone/21) (2020-08-13)

**Implemented enhancements:**

- Support of ElasticSearch 7  [\#279](https://github.com/TheHive-Project/Cortex/issues/279)

**Fixed bugs:**

- OAuth2 SSO Login Broken [\#264](https://github.com/TheHive-Project/Cortex/issues/264)

## [3.0.1](https://github.com/TheHive-Project/Cortex/milestone/24) (2020-04-24)

**Implemented enhancements:**

- Handle second/minute-rates limits on Flavors and Analyzers [\#164](https://github.com/TheHive-Project/Cortex/issues/164)
- Remove Elasticsearch cluster configuration option [\#230](https://github.com/TheHive-Project/Cortex/pull/230)
- Docker image has many CVE's open against it [\#238](https://github.com/TheHive-Project/Cortex/issues/238)
- Analyzer reports "no output" when it fails [\#241](https://github.com/TheHive-Project/Cortex/issues/241)
- Cortex logs the Play secret key at startup. [\#244](https://github.com/TheHive-Project/Cortex/issues/244)

**Fixed bugs:**

- Old non-existent analysers showing in Cortex after an upgrade [\#234](https://github.com/TheHive-Project/Cortex/issues/234)
- Missing dependency for cluster [\#239](https://github.com/TheHive-Project/Cortex/issues/239)
- Encoding issue causes invalid format for catalog file [\#240](https://github.com/TheHive-Project/Cortex/issues/240)
- Remove reference to google fonts [\#242](https://github.com/TheHive-Project/Cortex/issues/242)
- Fix error message display for failed analyzers/responders [\#243](https://github.com/TheHive-Project/Cortex/issues/243)

## [3.0.0](https://github.com/TheHive-Project/Cortex/milestone/23) (2019-09-05)

**Fixed bugs:**

- cortex 3.0.0-RC4 container : StreamSrv error popup spamming the setup page [\#210](https://github.com/TheHive-Project/Cortex/issues/210)

## [3.0.0-RC4](https://github.com/TheHive-Project/Cortex/milestone/22) (2019-07-11)

**Fixed bugs:**

- Login error after Cortex upgrade to 3 [\#199](https://github.com/TheHive-Project/Cortex/issues/199)
- docker version of cortex breaks when you don't create a user immediately [\#204](https://github.com/TheHive-Project/Cortex/issues/204)
- Responder run displayed as Analyzer run [\#207](https://github.com/TheHive-Project/Cortex/issues/207)

**Closed issues:**

- dockerhub sample uses the wrong port [\#203](https://github.com/TheHive-Project/Cortex/issues/203)
- docker version of cortex prints a lot of errors for auth failures [\#205](https://github.com/TheHive-Project/Cortex/issues/205)

## [3.0.0-RC3](https://github.com/TheHive-Project/Cortex/milestone/20) (2019-06-28)

**Implemented enhancements:**

- Upgrade frontend libraries [\#190](https://github.com/TheHive-Project/Cortex/issues/190)
- Add support of ElasticSearch 6 [\#191](https://github.com/TheHive-Project/Cortex/issues/191)
- Improve job details page [\#195](https://github.com/TheHive-Project/Cortex/issues/195)

**Fixed bugs:**

- Get user detials via API is available to non-admin users [\#194](https://github.com/TheHive-Project/Cortex/issues/194)

## [3.0.0-RC2](https://github.com/TheHive-Project/Cortex/milestone/19) (2019-05-03)

**Fixed bugs:**

- Docker container exposes tcp/9000 instead of tcp/9001 [\#166](https://github.com/TheHive-Project/Cortex/issues/166)
- Cortex will fail to run analyzers [\#182](https://github.com/TheHive-Project/Cortex/issues/182)
- Unable to load Analyzers with 3.0.0 [\#185](https://github.com/TheHive-Project/Cortex/issues/185)

## [3.0.0-RC1](https://github.com/TheHive-Project/Cortex/milestone/14) (2019-05-02)

**Implemented enhancements:**

- File extraction [\#120](https://github.com/TheHive-Project/Cortex/issues/120)
- Single sign-on support for Cortex [\#165](https://github.com/TheHive-Project/Cortex/issues/165)
- Update Copyright with year 2019 [\#168](https://github.com/TheHive-Project/Cortex/issues/168)
- Collapse job error messages by default in job history [\#171](https://github.com/TheHive-Project/Cortex/issues/171)
- Provide analyzers and responders packaged with docker [\#175](https://github.com/TheHive-Project/Cortex/issues/175)
- Use files to communicate with analyzer/responder [\#176](https://github.com/TheHive-Project/Cortex/issues/176)
- Remove size limitations [\#178](https://github.com/TheHive-Project/Cortex/issues/178)

**Fixed bugs:**

- Akka Dispatcher Blocked [\#170](https://github.com/TheHive-Project/Cortex/issues/170)
- SSO: Authentication module not found  [\#181](https://github.com/TheHive-Project/Cortex/issues/181)

## [2.1.3](https://github.com/TheHive-Project/Cortex/milestone/18) (2019-02-05)

**Implemented enhancements:**

- Add PAP property to jobs list [\#146](https://github.com/TheHive-Project/Cortex/issues/146)
- Add configuration for drone continuous integration [\#156](https://github.com/TheHive-Project/Cortex/issues/156)

**Fixed bugs:**

- GUI Search Function is broken [\#145](https://github.com/TheHive-Project/Cortex/issues/145)
- SinkDB analyzer could not find DIG in the Cortex docker image [\#147](https://github.com/TheHive-Project/Cortex/issues/147)
- PAP field is ignored from job modal [\#152](https://github.com/TheHive-Project/Cortex/issues/152)
- Unable to disable invalid responders [\#157](https://github.com/TheHive-Project/Cortex/issues/157)
- Wrong checks of role when an user is created [\#158](https://github.com/TheHive-Project/Cortex/issues/158)

**Closed issues:**

- conf/logback.xml: Rotate logs [\#62](https://github.com/TheHive-Project/Cortex/issues/62)
- Build Error on NodeJS 8 [\#142](https://github.com/TheHive-Project/Cortex/issues/142)

## [2.1.2](https://github.com/TheHive-Project/Cortex/milestone/17) (2018-10-12)

**Fixed bugs:**

- findSimilarJob function broken [\#144](https://github.com/TheHive-Project/Cortex/issues/144)

## [2.1.1](https://github.com/TheHive-Project/Cortex/milestone/16) (2018-10-12)

**Implemented enhancements:**

- Publish stable versions in beta package channels [\#138](https://github.com/TheHive-Project/Cortex/issues/138)
- Allow Cortex to use a custom root context [\#140](https://github.com/TheHive-Project/Cortex/issues/140)
- Change Debian dependencies [\#141](https://github.com/TheHive-Project/Cortex/issues/141)

**Fixed bugs:**

- Console output should not be logged in syslog [\#136](https://github.com/TheHive-Project/Cortex/issues/136)
- RPM update replace configuration file [\#137](https://github.com/TheHive-Project/Cortex/issues/137)
- Fix Cache column in analyzers admin page [\#139](https://github.com/TheHive-Project/Cortex/issues/139)

## [2.1.0](https://github.com/TheHive-Project/Cortex/milestone/15) (2018-09-25)

**Implemented enhancements:**

- Display cache configuration in analyzer admin page [\#123](https://github.com/TheHive-Project/Cortex/issues/123)
- Show PAP value in the Org > Analyzers screen [\#124](https://github.com/TheHive-Project/Cortex/issues/124)

**Fixed bugs:**

- MISP fails to run analyzers [\#128](https://github.com/TheHive-Project/Cortex/issues/128)
- Temporary files are not removed at the end of job [\#129](https://github.com/TheHive-Project/Cortex/issues/129)

## [2.1.0-RC1](https://github.com/TheHive-Project/Cortex/milestone/9) (2018-08-22)

**Implemented enhancements:**

- PAP as an analyzer restriction [\#65](https://github.com/TheHive-Project/Cortex/issues/65)
- Consider providing checksums for the release files [\#105](https://github.com/TheHive-Project/Cortex/issues/105)
- Automated response via Cortex [\#110](https://github.com/TheHive-Project/Cortex/issues/110)
- New TheHive-Project repository [\#112](https://github.com/TheHive-Project/Cortex/issues/112)

**Fixed bugs:**

- Fix redirection from Migration page to login on 401 error [\#114](https://github.com/TheHive-Project/Cortex/issues/114)
- Analyzers filter in Jobs History view is limited to 25 analyzers [\#116](https://github.com/TheHive-Project/Cortex/issues/116)
- First analyze of a "file" always fail, must re-run the analyze a second time [\#117](https://github.com/TheHive-Project/Cortex/issues/117)

**Closed issues:**

- Unable to update user [\#106](https://github.com/TheHive-Project/Cortex/issues/106)
- Refreshing analyzers does not refresh definition if already defined [\#115](https://github.com/TheHive-Project/Cortex/issues/115)

## [2.0.4](https://github.com/TheHive-Project/Cortex/milestone/13) (2018-04-13)

**Implemented enhancements:**

- Let a Read/Analyze User Display/Change their API Key [\#89](https://github.com/TheHive-Project/Cortex/issues/89)

**Fixed bugs:**

- Install python3 requirements for analyzers in public docker image [\#58](https://github.com/TheHive-Project/Cortex/issues/58)
- Cortex 2.0.3 docker container having cortex analyzer errors [\#90](https://github.com/TheHive-Project/Cortex/issues/90)
- Sort analyzers list by name [\#91](https://github.com/TheHive-Project/Cortex/issues/91)
- Wrong page redirection [\#92](https://github.com/TheHive-Project/Cortex/issues/92)
- Fix analyzer configurations icons [\#93](https://github.com/TheHive-Project/Cortex/issues/93)
- Updating users by orgAdmin users fails silently [\#94](https://github.com/TheHive-Project/Cortex/issues/94)
- Strictly filter the list of analyzers in the run dialog [\#95](https://github.com/TheHive-Project/Cortex/issues/95)

## [2.0.3](https://github.com/TheHive-Project/Cortex/milestone/12) (2018-04-12)

**Implemented enhancements:**

- Allow configuring auto artifacts extraction per analyzer [\#80](https://github.com/TheHive-Project/Cortex/issues/80)
- Change of global config for proxy is not reflected in analyzer's configurations [\#81](https://github.com/TheHive-Project/Cortex/issues/81)
- Display existing analyzers with invalid definition [\#82](https://github.com/TheHive-Project/Cortex/issues/82)
- Allow specifying a cache period per analyzer [\#85](https://github.com/TheHive-Project/Cortex/issues/85)
- Allow arbitrary parameters for a job [\#86](https://github.com/TheHive-Project/Cortex/issues/86)

**Fixed bugs:**

- Version Upgrade of Analyzer makes all Analyzers invisible for TheHive (Cortex2) [\#75](https://github.com/TheHive-Project/Cortex/issues/75)
- Refresh Analyzers button not working [\#83](https://github.com/TheHive-Project/Cortex/issues/83)

## [2.0.2](https://github.com/TheHive-Project/Cortex/milestone/11) (2018-04-04)

**Fixed bugs:**

- Session collision when TheHive & Cortex 2 share the same URL [\#70](https://github.com/TheHive-Project/Cortex/issues/70)
- Cortex 2 is not passing proxy variable to analyzers [\#71](https://github.com/TheHive-Project/Cortex/issues/71)
- Unable to disable analyzers [\#72](https://github.com/TheHive-Project/Cortex/issues/72)
- Silently failure when ElasticSearch is unreachable [\#76](https://github.com/TheHive-Project/Cortex/issues/76)

## [2.0.1](https://github.com/TheHive-Project/Cortex/milestone/10) (2018-03-30)

**Fixed bugs:**

- User can't change his password [\#67](https://github.com/TheHive-Project/Cortex/issues/67)
- Packages contain obsolete configuration sample [\#68](https://github.com/TheHive-Project/Cortex/issues/68)
- File upload component not working [\#69](https://github.com/TheHive-Project/Cortex/issues/69)

## [2.0.0](https://github.com/TheHive-Project/Cortex/milestone/1) (2018-03-30)

**Implemented enhancements:**

- Provide Secret Key auth to upstream service [\#2](https://github.com/TheHive-Project/Cortex/issues/2)
- Provide way to reload conf file for new API keys without shutdown. [\#3](https://github.com/TheHive-Project/Cortex/issues/3)
- Provide alternative paths for analyzers in addition to standard path.  [\#4](https://github.com/TheHive-Project/Cortex/issues/4)
- Persistence and Report Caching [\#5](https://github.com/TheHive-Project/Cortex/issues/5)
- Limit Rates and Respect Quotas [\#6](https://github.com/TheHive-Project/Cortex/issues/6)
- Local, LDAP, AD and API Key Authentication [\#7](https://github.com/TheHive-Project/Cortex/issues/7)
- Display analyzers only if necessary configuration values are set [\#14](https://github.com/TheHive-Project/Cortex/issues/14)

**Fixed bugs:**

- Error when clicking out of the "New Analysis" box [\#48](https://github.com/TheHive-Project/Cortex/issues/48)

## [1.1.4](https://github.com/TheHive-Project/Cortex/milestone/8) (2017-09-15)

**Implemented enhancements:**

- Disable analyzer in configuration file [\#32](https://github.com/TheHive-Project/Cortex/issues/32)
- Group ownership in Docker image prevents running on OpenShift [\#42](https://github.com/TheHive-Project/Cortex/issues/42)

**Fixed bugs:**

- Cortex removes the input details from failure reports [\#38](https://github.com/TheHive-Project/Cortex/issues/38)
- Display a error notification on analyzer start fail [\#39](https://github.com/TheHive-Project/Cortex/issues/39)

## [1.1.3](https://github.com/TheHive-Project/Cortex/milestone/7) (2017-06-29)

**Fixed bugs:**

- Error when parsing analyzer failure report [\#33](https://github.com/TheHive-Project/Cortex/issues/33)
- Problem Start Cortex on Ubuntu 16.04 [\#35](https://github.com/TheHive-Project/Cortex/issues/35)

## [1.1.2](https://github.com/TheHive-Project/Cortex/milestone/6) (2017-06-12)

**Implemented enhancements:**

- Initialize MISP modules at startup [\#28](https://github.com/TheHive-Project/Cortex/issues/28)
- Add page loader [\#30](https://github.com/TheHive-Project/Cortex/issues/30)

**Fixed bugs:**

- Error 500 in TheHive when a job is submited to Cortex [\#27](https://github.com/TheHive-Project/Cortex/issues/27)
- Cortex and MISP unclear and error-loop [\#29](https://github.com/TheHive-Project/Cortex/issues/29)
- jobstatus from jobs within cortex are not updated when status changes [\#31](https://github.com/TheHive-Project/Cortex/issues/31)

## [1.1.1](https://github.com/TheHive-Project/Cortex/milestone/5) (2017-05-17)

**Implemented enhancements:**

- MISP integration [\#21](https://github.com/TheHive-Project/Cortex/issues/21)

**Fixed bugs:**

- Missing logos and favicons [\#25](https://github.com/TheHive-Project/Cortex/issues/25)

## [1.1.0](https://github.com/TheHive-Project/Cortex/milestone/2) (2017-05-15)

**Implemented enhancements:**

- Display analyzers metadata [\#18](https://github.com/TheHive-Project/Cortex/issues/18)
- Scala code cleanup [\#19](https://github.com/TheHive-Project/Cortex/issues/19)
- Add support to .deb and .rpm package generation [\#20](https://github.com/TheHive-Project/Cortex/issues/20)

**Closed issues:**

- Use new logo and favicon [\#22](https://github.com/TheHive-Project/Cortex/issues/22)
- Display Cortex version on the footer [\#23](https://github.com/TheHive-Project/Cortex/issues/23)

## [1.0.2](https://github.com/TheHive-Project/Cortex/milestone/4) (2017-04-18)

**Fixed bugs:**

- Jobs list API doesn't take into account the limit param [\#11](https://github.com/TheHive-Project/Cortex/issues/11)
- Secure the usage of angular-ui-notification library [\#12](https://github.com/TheHive-Project/Cortex/issues/12)
- Global section in configuration file is ignored [\#13](https://github.com/TheHive-Project/Cortex/issues/13)
- Redirect to jobs list when a job is not found [\#16](https://github.com/TheHive-Project/Cortex/issues/16)

## [1.0.1](https://github.com/TheHive-Project/Cortex/milestone/3) (2017-03-22)

**Fixed bugs:**

- Fix page scroll issues [\#9](https://github.com/TheHive-Project/Cortex/issues/9)
