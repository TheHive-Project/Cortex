'use strict';

export default class AnalyzerConfigFormController {
  constructor(Tlps) {
    'ngInject';

    this.Tlps = Tlps;
    this.rateUnits = ['Day', 'Month'];
    this.rateLimitPattern = /^[0-9]{1,7}$/gi;
  }
}
