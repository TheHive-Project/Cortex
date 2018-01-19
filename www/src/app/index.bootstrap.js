'use strict';

// index.html page to dist folder
import '!!file-loader?name=[name].[ext]!../favicon.ico';
import '!!file-loader?name=[name].[ext]!../favicon-16x16.png';
import '!!file-loader?name=[name].[ext]!../favicon-32x32.png';
import '!!file-loader?name=[name].[ext]!../favicon-96x96.png';
import '!!file-loader?name=[name].[ext]!../favicon-128x128.png';
import '!!file-loader?name=[name].[ext]!../favicon-196x196.png';

import '../assets/images/logo-dark.svg';

// vendor files
import './index.vendor';

// main App module
import './index.module';

import '../assets/styles/sass/index.scss';

angular.element(document).ready(() => {
  angular.bootstrap(document, ['cortex'], {
    strictDi: true
  });
});
