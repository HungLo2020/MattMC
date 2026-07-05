-- Striker-12 Gun Logic (now using 'start' param)

local M = {}

function M.shoot(api)
    api:shootOnce(api:isShootingNeedConsumeAmmo())
end

function M.start_reload(api)
    local cache = {
        reloaded_count = 0,
        needed_count = api:getNeededAmmoAmount(),
        interrupted_time = -1,
    }
    api:cacheScriptData(cache)
    return true
end

local function getReloadTimingFromParam(param)
    local start = param.start * 1000
    local loop = param.loop * 1000
    local ending = param.ending * 1000
    local loop_feed = param.loop_feed * 1000
    return start, loop, ending, loop_feed
end

function M.tick_reload(api)
    local param = api:getScriptParams()
    local start, loop, ending, loop_feed = getReloadTimingFromParam(param)
    if (start == nil) then
        return NOT_RELOADING, -1
    end

    local reload_time = api:getReloadTime()
    local cache = api:getCachedScriptData()
    local interrupted_time = cache.interrupted_time

    if (interrupted_time ~= -1) then
        local int_time = reload_time - interrupted_time
        if (int_time >= ending) then
            return NOT_RELOADING, -1
        else
            return TACTICAL_RELOAD_FINISHING, ending - int_time
        end
    else
        if (not api:hasAmmoToConsume()) then
            interrupted_time = api:getReloadTime()
        end
    end

    local reloaded_count = cache.reloaded_count

    if (reloaded_count > 0 or reload_time > start) then
        local base_time = (reloaded_count) * loop + loop_feed
        base_time = base_time + start
        while (base_time < reload_time) do
            if (reloaded_count >= cache.needed_count) then
                break
            end
            reloaded_count = reloaded_count + 1
            base_time = base_time + loop
            api:consumeAmmoFromPlayer(1)
            api:putAmmoInMagazine(1)
        end
    end

    if (reloaded_count >= cache.needed_count) then
        interrupted_time = api:getReloadTime() - loop_feed + loop
    end

    cache.interrupted_time = interrupted_time
    cache.reloaded_count = reloaded_count
    api:cacheScriptData(cache)

    local total_time = cache.needed_count * loop + start
    return TACTICAL_RELOAD_FEEDING, total_time - reload_time
end

function M.interrupt_reload(api)
    local cache = api:getCachedScriptData()
    if (cache ~= nil and cache.interrupted_time == -1) then
        cache.interrupted_time = api:getReloadTime()
    end
end

return M
