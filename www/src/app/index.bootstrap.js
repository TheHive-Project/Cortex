'use strict';

// index.html page to dist folder
import '!!file-loader?name=[name].[ext]!../favicon.ico';

import '../assets/images/favicons/favicon-196x196.png';
import '../assets/images/favicons/favicon-96x96.png';
import '../assets/images/favicons/favicon-32x32.png';
import '../assets/images/favicons/favicon-16x16.png';
import '../assets/images/favicons/favicon-128.png';

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
