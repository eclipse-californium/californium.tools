/*global define*/

define([
    'jquery',
    'underscore',
    'backbone',
    'views/base',
    'templates',
    'views/sidebar'
], function($, _, Backbone, BaseView, JST, SidebarView) {
    'use strict';

    var LayoutView = BaseView.extend({
        template: JST['app/scripts/templates/layout.ejs'],

        initialize: function() {
            this.subviews = {
                '[role=sidebar]': new SidebarView()
            }
        }

    });

    return LayoutView;
});
