'use strict';

export default class AnalyzerConfigFormController {
  constructor(Tlps) {
    'ngInject';

    this.Tlps = Tlps;
    this.rateUnits = ['Day', 'Month'];
  }
}
