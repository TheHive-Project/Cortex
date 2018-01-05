'use strict';

import headerTpl from './header.html';
//import HeaderController from './header.controller';

import './header.scss';

export default class HeaderComponent {
  constructor() {
    this.templateUrl = headerTpl;
    //this.controller = FooterController;
  }
}
