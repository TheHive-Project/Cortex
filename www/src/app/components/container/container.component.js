'use strict';

import containerTpl from './container.html';

import './container.scss';

class ContainerController {
  constructor($log) {
    'ngInject';
    this.$log = $log;
  }
}

export default class ContainerComponent {
  constructor() {
    this.templateUrl = containerTpl;
    this.controller = ContainerController;
  }
}
