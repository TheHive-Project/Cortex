'use strict';

export default class JobController {
  constructor($log) {
    'ngInject';

    this.$log = $log;
  }

  $onInit() {
    this.protectDownloadsWith = this.main.config.config.protectDownloadsWith;
    this.hasFileArtifact = (this.job.report.artifacts || []).find(item => item.dataType === 'file');
  }
}