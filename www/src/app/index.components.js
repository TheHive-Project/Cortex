'use strict';

import ContainerComponent from './components/container/container.component';
import HeaderComponent from './components/header/header.component';
import FooterComponent from './components/footer/footer.component';

const componentsModule = angular.module('index.components', []);

componentsModule
  .component('container', new ContainerComponent())
  .component('header', new HeaderComponent())
  .component('footer', new FooterComponent());

export default componentsModule;
