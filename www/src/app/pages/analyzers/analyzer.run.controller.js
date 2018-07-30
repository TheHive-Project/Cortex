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
    this.initialAnalyzers = [];
  }

  $onInit() {
    this.initialAnalyzers = this.getActiveIds();
    this.formData = {
      analyzers: this.analyzers,
      dataTypes: uniq(_.flatten(_.map(this.analyzers, 'dataTypeList'))),
      ids: this.getActiveIds().join(',')
    };
    this.observable.tlp = this.observable.tlp || this.Tlps[2].value;
    this.observable.pap = this.observable.pap || this.Tlps[2].value;
  }

  getActiveIds() {
    return _.map(_.filter(this.analyzers, item => item.active === true), 'id');
  }

  isFile() {
    return this.observable.dataType === 'file';
  }

  clearData() {
    delete this.observable.data;
    delete this.observable.attachment;

    _.each(this.analyzers, item => {
      if (this.initialAnalyzers.indexOf(item.id) === -1) {
        item.active = false;
      }
    });

    if (this.initialAnalyzers.length > 0) {
      this.formData.ids = this.initialAnalyzers.join(',');
    } else {
      delete this.formData.ids;
    }
  }

  toggleAnalyzer(analyzer) {
    analyzer.active = !analyzer.active;

    this.formData.ids = this.getActiveIds().join(',');
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