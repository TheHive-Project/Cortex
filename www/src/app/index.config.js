/*global NODE_ENV*/
'use strict';

function config(
  $logProvider,
  $httpProvider,
  $compileProvider,
  $locationProvider,
  MaintenanceServiceProvider,
  NotificationProvider,
  localStorageServiceProvider
) {
  'ngInject';

  $httpProvider.defaults.xsrfCookieName = 'CORTEX-XSRF-TOKEN';
  $httpProvider.defaults.xsrfHeaderName = 'X-CORTEX-XSRF-TOKEN';

  $logProvider.debugEnabled(false);

  if (NODE_ENV === 'production') {
    $logProvider.debugEnabled(false);
    $compileProvider.debugInfoEnabled(false);
  }

  $locationProvider.html5Mode(false);

  MaintenanceServiceProvider.setSuccessState('index');

  NotificationProvider.setOptions({
    delay: 10000,
    startTop: 20,
    startRight: 10,
    verticalSpacing: 20,
    horizontalSpacing: 20,
    positionX: 'left',
    positionY: 'bottom'
  });

  // Configure local storage service
  localStorageServiceProvider.setPrefix('cortex');
}

export default config;
