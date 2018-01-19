'use strict';

import _ from 'lodash/core';

export default class AnalyzerRunController {
  constructor($log, $uibModalInstance, Tlps, initialData) {
    'ngInject';

    this.$log = $log;
    this.$uibModalInstance = $uibModalInstance;

    this.initialData = initialData;
    this.Tlps = Tlps;

    this.formData = {
      analyzer: this.initialData.analyzer,
      tlp: Tlps[2],
      dataType: this.initialData.dataType
    };
  }

  isFile() {
    return this.formData.dataType === 'file';
  }

  clearData() {
    delete this.formData.data;
    delete this.formData.attachment;
    delete this.formData.ids;

    _.each(this.initialData.analyzers, function(item) {
      item.active = false;
    });
  }

  toggleAnalyzer(analyzer) {
    analyzer.active = !analyzer.active;

    let active = _.filter(this.initialData.analyzers, function(item) {
      return item.active === true;
    });

    this.formData.ids = _.map(active, 'id').join(',');
  }

  ok() {
    this.$uibModalInstance.close(this.formData);
  }

  cancel() {
    this.$uibModalInstance.dismiss('cancel');
  }
}
