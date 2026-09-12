-- Issue #146: explicitly approved aliases for 10 hosts. Never infer additional aliases.
-- Match the existing unique host.name, not environment-specific IDs; preserve host data.
DO $$
DECLARE
    inserted_count integer;
BEGIN
    INSERT INTO host_alias (host_id, name)
    SELECT h.id, seed.alias
    FROM (VALUES
        ('연세대학교', '연대'),
        ('고려대학교', '고대'),
        ('홍익대학교', '홍대'),
        ('한국외국어대학교', '외대'),
        ('한국외국어대학교', '한국외대'),
        ('이화여자대학교', '이대'),
        ('이화여자대학교', '이화여대'),
        ('숙명여자대학교', '숙대'),
        ('숙명여자대학교', '숙명여대'),
        ('서울여자대학교', '서울여대'),
        ('서울여자대학교', '설여대'),
        ('동덕여자대학교', '동덕여대'),
        ('동덕여자대학교', '동덕대'),
        ('덕성여자대학교', '덕성여대'),
        ('덕성여자대학교', '덕성대'),
        ('건국대학교', '건대')
    ) AS seed(host_name, alias)
    JOIN host h ON h.name = seed.host_name;

    GET DIAGNOSTICS inserted_count = ROW_COUNT;
    IF inserted_count <> 16 THEN
        RAISE EXCEPTION 'Host alias seed requires all 10 named hosts (16 aliases); inserted % aliases. Check missing or renamed hosts.', inserted_count;
    END IF;
END $$;
