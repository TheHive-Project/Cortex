'use strict';

import footerTpl from './footer.html';
import FooterController from './footer.controller';

export default class FooterComponent {
    constructor() {
        this.templateUrl = footerTpl;
        this.controller = FooterController;
    }
}