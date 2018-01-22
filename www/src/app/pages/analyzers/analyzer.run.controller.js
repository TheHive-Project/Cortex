'use strict';

import _ from 'lodash/core';
import uniq from 'lodash/uniq';

export default class AnalyzerRunController {
  constructor($log, $uibModalInstance, Tlps, analyzers, observable) {
    'ngInject';

    this.$log = $log;
    this.$uibModalInstance = $uibModalInstance;

    this.Tlps = Tlps;
    this.analyzers = analyzers;
    this.observable = observable;
  }

  $onInit() {
    this.formData = {
      analyzers: this.analyzers,
      dataTypes: uniq(_.flatten(_.map(this.analyzers, 'dataTypeList'))),
      ids: this.getActiveIds()
    };
    this.observable.tlp = this.observable.tlp || this.Tlps[2].value;
  }

  getActiveIds() {
    return _.map(
      _.filter(this.analyzers, item => item.active === true),
      'id'
    ).join(',');
  }

  isFile() {
    return this.observable.dataType === 'file';
  }

  clearData() {
    delete this.observable.data;
    delete this.observable.attachment;
    delete this.formData.ids;

    _.each(this.analyzers, item => {
      item.active = false;
    });
  }

  toggleAnalyzer(analyzer) {
    analyzer.active = !analyzer.active;

    this.formData.ids = this.getActiveIds();
  }

  ok() {
    let ids = [];

    if (this.analyzers.length === 1) {
      ids = [this.analyzers[0].id];
    } else {
      ids = this.formData.ids.split(',');
    }

    this.$uibModalInstance.close({
      analyzerIds: ids,
      observable: this.observable
    });
  }

  cancel() {
    this.$uibModalInstance.dismiss('cancel');
  }
}
