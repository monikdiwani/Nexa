import random
from datetime import datetime, timedelta

NUM_COMMITS = 101
START_DATE = datetime(2026, 4, 1)
END_DATE = datetime(2026, 6, 10)
EXCLUDED_DATES = {
    datetime(2026, 3, 12).date(), datetime(2026, 3, 19).date(), datetime(2026, 3, 20).date(), datetime(2026, 3, 26).date(),
    datetime(2026, 4, 12).date(), datetime(2026, 4, 13).date(), datetime(2026, 4, 17).date(), datetime(2026, 4, 18).date(), datetime(2026, 4, 19).date(), datetime(2026, 4, 22).date(), datetime(2026, 4, 23).date(), datetime(2026, 4, 25).date(),
    datetime(2026, 5, 3).date(), datetime(2026, 5, 4).date(), datetime(2026, 5, 5).date(), datetime(2026, 5, 6).date(), datetime(2026, 5, 7).date(), datetime(2026, 5, 8).date(), datetime(2026, 5, 10).date(), datetime(2026, 5, 11).date(), datetime(2026, 5, 16).date(), datetime(2026, 5, 20).date(), datetime(2026, 5, 24).date(), datetime(2026, 5, 25).date(), datetime(2026, 5, 27).date(), datetime(2026, 5, 28).date(), datetime(2026, 5, 30).date(), datetime(2026, 5, 31).date(),
    datetime(2026, 6, 1).date(), datetime(2026, 6, 6).date(), datetime(2026, 6, 7).date(), datetime(2026, 6, 13).date()
}
MARCH_7_DATE = datetime(2026, 3, 7).date()

# Allowed days pool for the remaining 55 commits
allowed_days = []
current_date = START_DATE
while current_date <= END_DATE:
    # Explicitly skip June 1, 3, 5 from random pool because we hardcode them later
    skip_hardcoded = {datetime(2026, 6, 1).date(), datetime(2026, 6, 3).date(), datetime(2026, 6, 5).date()}
    if current_date.date() not in EXCLUDED_DATES and current_date.date() not in skip_hardcoded:
        allowed_days.append(current_date)
    current_date += timedelta(days=1)

# Pick exactly 6 random days from the allowed pool
selected_days = random.sample(allowed_days, 6)
selected_days.sort()

# Distribute 55 commits among these 6 days
distribution = [12, 10, 10, 8, 8, 7]
random.shuffle(distribution)

# Generate timestamps
timestamps = []

def add_day(day_date, count):
    for _ in range(count):
        hour = random.randint(10, 23)
        minute = random.randint(0, 59)
        second = random.randint(0, 59)
        dt = datetime(day_date.year, day_date.month, day_date.day, hour, minute, second)
        timestamps.append(int(dt.timestamp()))

# Hardcoded days
add_day(MARCH_7_DATE, 1)
add_day(datetime(2026, 5, 7).date(), 10)
add_day(datetime(2026, 5, 8).date(), 15)
add_day(datetime(2026, 5, 30).date(), 5)
add_day(datetime(2026, 6, 1).date(), 5)
add_day(datetime(2026, 6, 3).date(), 5)
add_day(datetime(2026, 6, 5).date(), 5)

for day, count in zip(selected_days, distribution):
    add_day(day.date(), count)

timestamps.sort()

assert len(timestamps) == NUM_COMMITS, f"Generated {len(timestamps)} but expected {NUM_COMMITS}"

with open('timestamps.txt', 'w') as f:
    for ts in timestamps:
        f.write(f"{ts}\n")

print(f"Generated {len(timestamps)} perfectly authentic timestamps!")
print(f"March 7th: 1 commit")
print(f"May 7th: 10 commits")
print(f"May 8th: 15 commits")
print(f"May 30th: 5 commits")
print(f"June 1st: 5 commits")
print(f"June 3rd: 5 commits")
print(f"June 5th: 5 commits")
for day, count in zip(selected_days, distribution):
    print(f"{day.date()}: {count} commits")
