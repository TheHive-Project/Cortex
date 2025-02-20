'use strict';

import footerTpl from './footer.html';

class FooterController {
  constructor($log, $scope) {
    'ngInject';
    this.$log = $log;
    $scope.date = new Date();
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
