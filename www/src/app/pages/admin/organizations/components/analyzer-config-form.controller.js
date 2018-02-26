'use strict';

export default class AnalyzerConfigFormController {
  constructor($log) {
    'ngInject';

    this.$log = $log;

    this.rateUnits = ['Day', 'Month'];
    this.formData = {};
  }

  $onInit() {
    const { name, configuration } = this.analyzer;

    this.formData = {
      name,
      configuration
    };
  }

  typeOf(config) {
    return `${config.multi ? 'multi-' : ''}${config.type}`;
  }

  addOption(config) {
    let defaultValues = {
      string: null,
      number: 0,
      boolean: true
    };
    this.analyzer.configuration[config.name].push(defaultValues[config.type]);
  }
}
