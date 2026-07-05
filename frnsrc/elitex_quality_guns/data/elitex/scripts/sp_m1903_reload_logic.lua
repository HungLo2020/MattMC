local M = {}

function M.start_reload(api)
    return true
end

-- 🔥 SAFE PARAM PARSER (MATCHES YOUR JSON)
local function getReloadTimingFromParam(param)
    if not param then return nil, nil end

    local reload_feed = {}
    local reload_cooldown = {}
    local empty_feed = {}
    local empty_cooldown = {}

    -- EMPTY (only one slot)
    empty_feed[1] = param.reload_empty_feed
    empty_cooldown[1] = param.reload_empty_cooldown

    -- tactical (ammo based → reversed like your animations)
    reload_feed[1] = param.reload_4_feed
    reload_cooldown[1] = param.reload_4_cooldown

    reload_feed[2] = param.reload_3_feed
    reload_cooldown[2] = param.reload_3_cooldown

    reload_feed[3] = param.reload_2_feed
    reload_cooldown[3] = param.reload_2_cooldown

    reload_feed[4] = param.reload_1_feed
    reload_cooldown[4] = param.reload_1_cooldown

    -- ✅ VALIDATION + ms conversion
    for i = 1, 4 do
        if reload_feed[i] == nil or reload_cooldown[i] == nil then
            return nil, nil
        end

        reload_feed[i] = reload_feed[i] * 1000
        reload_cooldown[i] = reload_cooldown[i] * 1000
    end

    if empty_feed[1] == nil or empty_cooldown[1] == nil then
        return nil, nil
    end

    empty_feed[1] = empty_feed[1] * 1000
    empty_cooldown[1] = empty_cooldown[1] * 1000

    return reload_feed, reload_cooldown, empty_feed, empty_cooldown
end

local function isReloadingEmpty(stateType)
    return stateType == EMPTY_RELOAD_FEEDING or stateType == EMPTY_RELOAD_FINISHING
end

local function isReloadingTactical(stateType)
    return stateType == TACTICAL_RELOAD_FEEDING or stateType == TACTICAL_RELOAD_FINISHING
end

local function finishReload(api, is_tactical)
    local needAmmoCount = api:getNeededAmmoAmount()

    if api:isReloadingNeedConsumeAmmo() then
        api:putAmmoInMagazine(api:consumeAmmoFromPlayer(needAmmoCount))
    else
        api:putAmmoInMagazine(needAmmoCount)
    end

    -- chamber logic for empty reload
    if not is_tactical then
        local i = api:removeAmmoFromMagazine(1)
        if i ~= 0 then
            api:setAmmoInBarrel(true)
        end
    end
end

function M.tick_reload(api)

    local param = api:getScriptParams()
    local reload_feed, reload_cooldown, empty_feed, empty_cooldown =
    getReloadTimingFromParam(param)

    if reload_feed == nil then
        return NOT_RELOADING, -1
    end

    local ammo = api:getAmmoCount()
    local hasBullet = api:hasBulletInBarrel()
    local totalAmmo = ammo + (hasBullet and 1 or 0)

    -- prevent overflow
    if totalAmmo >= 5 then
        return NOT_RELOADING, -1
    end

    -- 🔥 SAFE LEVEL (1–4 ONLY)
    local level = math.max(1, math.min(totalAmmo, 4))

    local progressTime = api:getReloadTime()
    local oldStateType = api:getReloadStateType()

    local stateType = NOT_RELOADING
    local countDown = -1

    if isReloadingEmpty(oldStateType) then

        local feed_time = empty_feed[1]
        local finish_time = empty_cooldown[1]

        if progressTime < feed_time then
            stateType = EMPTY_RELOAD_FEEDING
            countDown = feed_time - progressTime
        elseif progressTime < finish_time then
            stateType = EMPTY_RELOAD_FINISHING
            countDown = finish_time - progressTime
        else
            stateType = NOT_RELOADING
        end

    elseif isReloadingTactical(oldStateType) then

        local feed_time = reload_feed[level]
        local finish_time = reload_cooldown[level]

        if progressTime < feed_time then
            stateType = TACTICAL_RELOAD_FEEDING
            countDown = feed_time - progressTime
        elseif progressTime < finish_time then
            stateType = TACTICAL_RELOAD_FINISHING
            countDown = finish_time - progressTime
        else
            stateType = NOT_RELOADING
        end

    else
        return NOT_RELOADING, -1
    end

    -- apply ammo (same safe pattern as working script)
    if oldStateType == EMPTY_RELOAD_FEEDING and oldStateType ~= stateType then
        finishReload(api, false)
    end

    if oldStateType == TACTICAL_RELOAD_FEEDING and oldStateType ~= stateType then
        finishReload(api, true)
    end

    return stateType, countDown
end

return M