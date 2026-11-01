/**
 * Nuvio Provider: FullRaceReplay (Formula 1 & MotoGP)
 * Backend: f1.fullracereplay.com / motogp.fullracereplay.com WordPress REST API
 */

const F1_API = 'https://f1.fullracereplay.com/wp-json/wp/v2/posts';
const MOTOGP_API = 'https://motogp.fullracereplay.com/wp-json/wp/v2/posts';

function getCatalog(catalogId, page) {
    var p = page || 1;
    var api = catalogId === 'motogp' ? MOTOGP_API : F1_API;
    var url = api + '?per_page=12&page=' + p;

    return fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } })
        .then(function(res) { return res.json(); })
        .then(function(posts) {
            var items = [];
            for (var i = 0; i < posts.length; i++) {
                var post = posts[i];
                var title = post.title ? post.title.rendered.replace(/&#8211;/g, '-').replace(/&amp;/g, '&') : 'Race Replay';
                var content = post.content ? post.content.rendered : '';
                var m = /src=["'](https?:\/\/[^"']+\.(?:webp|jpg|png|jpeg))["']/.exec(content);
                var poster = m ? m[1] : '';

                items.push({
                    id: post.link,
                    title: title,
                    poster: poster,
                    type: 'series'
                });
            }
            return items;
        });
}

function search(query) {
    var q = encodeURIComponent(query.trim());
    return Promise.all([
        fetch(F1_API + '?search=' + q + '&per_page=6', { headers: { 'User-Agent': 'Mozilla/5.0' } }).then(function(r) { return r.json(); }).catch(function() { return []; }),
        fetch(MOTOGP_API + '?search=' + q + '&per_page=6', { headers: { 'User-Agent': 'Mozilla/5.0' } }).then(function(r) { return r.json(); }).catch(function() { return []; })
    ]).then(function(results) {
        var all = results[0].concat(results[1]);
        var items = [];
        for (var i = 0; i < all.length; i++) {
            var post = all[i];
            var title = post.title ? post.title.rendered.replace(/&#8211;/g, '-').replace(/&amp;/g, '&') : 'Race Replay';
            var content = post.content ? post.content.rendered : '';
            var m = /src=["'](https?:\/\/[^"']+\.(?:webp|jpg|png|jpeg))["']/.exec(content);
            var poster = m ? m[1] : '';

            items.push({
                id: post.link,
                title: title,
                poster: poster,
                type: 'series'
            });
        }
        return items;
    });
}

function getDetails(url) {
    return fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } })
        .then(function(res) { return res.text(); })
        .then(function(html) {
            var titleMatch = /<h1[^>]*>([\s\S]*?)<\/h1>/.exec(html);
            var title = titleMatch ? titleMatch[1].replace(/&#8211;/g, '-').replace(/<[^>]+>/g, '').trim() : 'Race Replay';
            
            var apividMatch = /https?:\/\/apivid\.website\/(?:watch|download)\.php\?id=([a-zA-Z0-9]+)/.exec(html);
            var watchUrl = apividMatch ? 'https://apivid.website/watch.php?id=' + apividMatch[1] : url;

            return {
                id: url,
                title: title,
                type: 'series',
                episodes: [
                    {
                        id: watchUrl,
                        title: 'Full Race Replay',
                        episodeNumber: 1
                    }
                ]
            };
        });
}

function getStreams(watchUrl) {
    return fetch(watchUrl, {
        headers: {
            'User-Agent': 'Mozilla/5.0',
            'Referer': 'https://fullracereplay.com/'
        }
    })
    .then(function(res) { return res.text(); })
    .then(function(html) {
        var streams = [];
        var mSources = /sources\s*=\s*(\[[^\]]+\])/.exec(html);
        if (mSources) {
            try {
                var arr = JSON.parse(mSources[1]);
                for (var i = 0; i < arr.length; i++) {
                    var s = arr[i];
                    streams.push({
                        name: 'Server ' + (i + 1) + ' (' + (s.indexOf('ok.ru') !== -1 ? 'OK.ru HD' : 'Stream') + ')',
                        url: s,
                        quality: '1080p',
                        headers: { 'Referer': watchUrl }
                    });
                }
            } catch(e) {}
        }
        return streams;
    });
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        getCatalog: getCatalog,
        search: search,
        getDetails: getDetails,
        getStreams: getStreams
    };
}
