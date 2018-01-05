/*global NODE_ENV*/
'use strict';

function config($logProvider, $compileProvider) {
	'ngInject';

    $logProvider.debugEnabled(true);

    if (NODE_ENV === 'production') {
        $logProvider.debugEnabled(false);
        $compileProvider.debugInfoEnabled(false);
    }

  
}

export default config;
