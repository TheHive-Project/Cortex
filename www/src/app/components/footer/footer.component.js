'use strict';

import footerTpl from './footer.html';

class FooterController {
  constructor($log) {
    'ngInject';
    this.$log = $log;
  }
}

export default class FooterComponent {
  constructor() {
    this.templateUrl = footerTpl;
    this.controller = FooterController;
    this.require = {
      main: '^mainPage'
    };
  }
}
