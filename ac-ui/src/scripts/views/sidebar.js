/*global define*/

define([
    'jquery',
    'underscore',
    'backbone',
    'views/base',
    'templates'
], function($, _, Backbone, BaseView, JST) {
    'use strict';

    var SideBarView = BaseView.extend({
        template: JST['app/scripts/templates/sidebar.ejs'],

        tagName: 'div',

        id: '',

        className: '',

        events: {
            'click .refresh': 'refresh'
        },

        initialize: function() {
            this.listenTo(window.AppInstanceCollection, 'change', this.render);
            this.listenTo(window.AppInstanceCollection, 'add', this.render);
            this.listenTo(window.AppInstanceCollection, 'remove', this.render);
            this.listenTo(window.AppInstanceCollection, 'reset', this.render);
            this.listenTo(window.AppCollection, 'change', this.render);
            this.listenTo(window.AppCollection, 'remove', this.render);
            this.listenTo(window.AppCollection, 'add', this.render);
            this.listenTo(window.AppCollection, 'reset', this.render);

            this.listenTo(window.LibCollection, 'change', this.render);
            this.listenTo(window.LibCollection, 'remove', this.render);
            this.listenTo(window.LibCollection, 'add', this.render);
            this.listenTo(window.LibCollection, 'reset', this.render);

            Router.on('route', this.update_active, this);
        },

        refresh: function() {
            window.AppCollection.fetch();
            window.LibCollection.fetch();
            window.AppInstanceCollection.fetch({
                success: function(collection, response) {
                    // notify AppInstance model to synchronize with server
                    collection.trigger('updated');
                }
            });
        },

        before_render: function() {
            this.scroll_position = this.$('.nav').scrollTop();
        },

        get_data: function() {
            console.log(window.LibCollection.toJSON());
            return {
                app_instances: window.AppInstanceCollection.toJSON(),
                libs: window.LibCollection.toJSON(),
                apps: window.AppCollection.toJSON()
            };
        },

        after_render: function() {
            var self = this;
            $(window).resize(function() {
                var windowHeight = $(window).height() - 75;
                self.$('.nav').css('height', windowHeight);
            }).resize();

            this.update_active()
            this.$('.nav').scrollTop(this.scroll_position);
            setTimeout(function() {
                self.$('.nav').scrollTop(self.scroll_position);
            });
        },

        update_active: function() {
            var item = null;
            var matchlength = -1;
            var active = window.location.hash + "";
            if (active === "") {
                active = "#";
            }
            var menu_items = this.$el.find('li');
            menu_items.each(function() {
                var url = $(this).find('a').data('match');
                if (url == undefined) {
                    url = $(this).find('a').attr('href');
                }
                if (active.startsWith(url) && url.length > matchlength) {
                    item = $(this);
                    matchlength = url.length;
                }
            });
            item && menu_items.removeClass('active');
            item && item.addClass('active');
        }

    });

    return SideBarView;
});
