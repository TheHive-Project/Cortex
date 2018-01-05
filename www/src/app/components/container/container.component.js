'use strict';

import containerTpl from './container.html';
import ContainerController from './container.controller';

import './container.scss';

export default class ContainerComponent {
  constructor() {
    this.templateUrl = containerTpl;
    this.controller = ContainerController;
  }
}
