'use strict';

export default class AnalyzerConfigFormController {
  constructor($log, Tlps) {
    'ngInject';

    this.$log = $log;
    this.Tlps = Tlps;

    this.rateUnits = ['Day', 'Month'];
    // this.formData = {};
  }

  // $onInit() {
  //   const { name, configuration } = this.analyzer;

  //   this.formData = {
  //     name,
  //     configuration
  //   };
  // }
}
