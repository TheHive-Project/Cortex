/*global NODE_ENV*/
'use strict';

function config(
  $logProvider,
  $compileProvider,
  $locationProvider,
  MaintenanceServiceProvider,
  NotificationProvider
) {
  'ngInject';

  $logProvider.debugEnabled(true);

  if (NODE_ENV === 'production') {
    $logProvider.debugEnabled(false);
    $compileProvider.debugInfoEnabled(false);
  }

  $locationProvider.html5Mode(false);

  MaintenanceServiceProvider.setSuccessState('main.analyzers');

  NotificationProvider.setOptions({
    delay: 10000,
    startTop: 20,
    startRight: 10,
    verticalSpacing: 20,
    horizontalSpacing: 20,
    positionX: 'left',
    positionY: 'bottom'
  });
}

export default config;
