local M = {}

function M.shoot(api)
    api:shootOnce(api:isShootingNeedConsumeAmmo())

    if (api:getAttachment("MUZZLE") == "hamster:gas_tube") then
        if (api:removeAmmoFromMagazine(1) ~= 0) then
            api:setAmmoInBarrel(true);
        end
        return false
    end

    local cache = api:getCachedScriptData()
    if (cache == nil) then
        cache = {
            shoot_mode = 0
        }
    end
    if (api:getAimingProgress() == 0) then
        cache.shoot_mode = 1
    else
        cache.shoot_mode = 0
    end
    api:cacheScriptData(cache)
end

function M.start_bolt(api)
    -- Return true to start ticking, since there are nothing needed to be check
    return true
end

function M.tick_bolt(api)
    -- 调用缓存数据
    local cache = api:getCachedScriptData()
    -- 获取data中的数据
    local total_bolt_time = api:getScriptParams().bolt_time * 1000

    if (cache ~= nil) then
        -- 如果当前是腰射状态，则赋值覆盖掉原来的拉栓时长数据
        if (cache.shoot_mode ~= nil and cache.shoot_mode == 1) then
            total_bolt_time = api:getScriptParams().rapid_bolt_time * 1000
        end
    end

    if (total_bolt_time == nil) then
        return false
    end
    if (api:getBoltTime() < total_bolt_time) then
        return true
    else
        if (api:removeAmmoFromMagazine(1) ~= 0) then
            api:setAmmoInBarrel(true);
        end
        return false
    end
end

function M.start_reload(api)
    -- Initialize cache that will be used in reload ticking
    local cache = {
        reloaded_count = 0,
        needed_count = api:getNeededAmmoAmount(),
        is_tactical = api:getReloadStateType() == TACTICAL_RELOAD_FEEDING,
        interrupted_time = -1,
    }
    if(api:hasAmmoInBarrel() and api:getAttachment("MUZZLE") ~= "hamster:gas_tube")then
        cache.needed_count = cache.needed_count - 1
    end
    api:cacheScriptData(cache)
    -- Return true to start ticking
    return true
end

local function getReloadTimingFromParam(param)
    -- Need to convert time from seconds to milliseconds
    local intro = param.intro * 1000
    local intro_empty = param.intro_empty * 1000
    local loop = param.loop * 1000
    local ending = param.ending * 1000
    local ending_empty_feed = param.ending_empty_feed * 1000
    local clip_load = param.clip_load * 1000
    local clip_load_feed = param.clip_load_feed * 1000
    local loop_feed = param.loop_feed * 1000
    local mag_feed = param.mag_feed * 1000
    local mag = param.mag * 1000
    local mag_empty = param.mag_empty * 1000
    -- Check if any timing is nil
    if (intro == nil or intro_empty == nil or loop == nil or ending == nil or ending_empty_feed == nil or clip_load == nil or clip_load_feed == nil or loop_feed == nil or mag_feed == nil or mag == nil or mag_empty == nil) then
        return nil
    end
    return intro, intro_empty, loop, ending, ending_empty_feed, clip_load, clip_load_feed, loop_feed, mag_feed, mag, mag_empty
end

function M.tick_reload(api)
    -- 获取所有装弹所需要的数据
    local param = api:getScriptParams();
    local intro, intro_empty, loop, ending, ending_empty_feed, clip_load, clip_load_feed, loop_feed, mag_feed, mag, mag_empty = getReloadTimingFromParam(param)
    -- 获取从开始装弹到现在的时间，单位为毫秒
    local reload_time = api:getReloadTime()
    -- 调用在装弹开始时初始化写入的缓存数据
    local cache = api:getCachedScriptData()
    local interrupted_time = cache.interrupted_time
    -- Handle interrupting reload
    if (api:getAttachment("MUZZLE") == "hamster:gas_tube") then
        if (not cache.is_tactical) then
            if (reload_time < mag_feed) then
                return TACTICAL_RELOAD_FEEDING, mag_feed - reload_time
            elseif (reload_time >= mag_feed and reload_time < mag_empty) then
                if (cache.needed_count > 0) then
                    api:putAmmoInMagazine(api:isReloadingNeedConsumeAmmo()
                            and api:consumeAmmoFromPlayer(cache.needed_count) or cache.needed_count)
                    cache.needed_count = 0
                end
                return TACTICAL_RELOAD_FINISHING, mag_empty - reload_time
            else
                return NOT_RELOADING, -1
            end
        else
            if (reload_time < mag_feed) then
                return EMPTY_RELOAD_FEEDING, mag_feed - reload_time
            elseif (reload_time >= mag_feed and reload_time < mag) then
                if (cache.needed_count > 0) then
                    api:putAmmoInMagazine(api:isReloadingNeedConsumeAmmo()
                            and api:consumeAmmoFromPlayer(cache.needed_count) or cache.needed_count)
                    cache.needed_count = 0
                end
                return EMPTY_RELOAD_FINISHING, mag - reload_time
            else
                return NOT_RELOADING, -1
            end
        end
    end
    if (cache.interrupted_time ~= -1) then
        local int_time = reload_time - cache.interrupted_time
        if (not cache.is_tactical) then
            if (int_time > ending_empty_feed) then
                if(not api:hasAmmoInBarrel()) then
                    if (api:removeAmmoFromMagazine(1) ~= 0) then
                        api:setAmmoInBarrel(true);
                    end
                end
            end
            if(int_time >= ending) then
                return NOT_RELOADING, -1
            else
                return EMPTY_RELOAD_FINISHING, ending - int_time
            end
        else
            if(int_time >= ending) then
                return NOT_RELOADING, -1
            else
                return TACTICAL_RELOAD_FINISHING, ending - int_time
            end
        end
    else
        if (not api:hasAmmoToConsume()) then
            interrupted_time = api:getReloadTime()
        end
    end

    local reloaded_count = cache.reloaded_count;
    -- Load the ammo into the magazine one by one
    if (reloaded_count >= 0) then
        local base_time = reloaded_count * loop
        if(reloaded_count >= 5) then
            base_time = (math.floor(reloaded_count / 5) * clip_load) + (reloaded_count % 5) * loop
        end
        if (cache.needed_count - reloaded_count >= 5) then
            base_time = base_time + clip_load_feed
        else
            base_time = base_time + loop_feed
        end
        if (not cache.is_tactical) then
            base_time = base_time + intro_empty
        else
            base_time = base_time + intro
        end
        while (base_time < reload_time) do
            if (reloaded_count > cache.needed_count) then
                break
            end
            if (cache.needed_count - reloaded_count >= 5) then
                reloaded_count = reloaded_count + 5
                base_time = base_time + clip_load
                api:putAmmoInMagazine(api:isReloadingNeedConsumeAmmo() and api:consumeAmmoFromPlayer(5) or 5)
            else
                reloaded_count = reloaded_count + 1
                base_time = base_time + loop
                api:consumeAmmoFromPlayer(1)
                api:putAmmoInMagazine(1)
            end
        end
    end

    -- Write back cache
    if (reloaded_count >= cache.needed_count) then
        interrupted_time = api:getReloadTime() - loop_feed + loop
    end
    cache.interrupted_time = interrupted_time
    cache.reloaded_count = reloaded_count
    api:cacheScriptData(cache)
    -- return reloadstate
    local total_time = cache.needed_count * loop
    if (not cache.is_tactical) then
        total_time = total_time + intro
        return EMPTY_RELOAD_FEEDING, total_time - reload_time
    else
        total_time = total_time + intro
        return TACTICAL_RELOAD_FEEDING, total_time - reload_time
    end
end

function M.interrupt_reload(api)
    local cache = api:getCachedScriptData()
    if (cache ~= nil and cache.interrupted_time == -1) then
        cache.interrupted_time = api:getReloadTime()
    end
end

return M