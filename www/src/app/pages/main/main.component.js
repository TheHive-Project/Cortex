'use strict';

import MainController from './main.controller';
import mainTpl from './main.html';

export default class MainComponent {
    constructor() {
        this.controller = MainController;
        this.templateUrl = mainTpl;
    }
}