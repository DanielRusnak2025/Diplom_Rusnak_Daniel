# Model Comparison — 2026-11-04

## Configuration

- **Ollama version:** 0.30.3
- **Models:**

  | Model | Size |
  |---|---|
  | qwen3:8b | 5,2 GB |
  | llama3.2 | 2,0 GB |

- **Articles:** 62 fetched, 9 failed to fetch, 19 pre-filtered (no AI call) (71 total URLs from queue)
- **Warmup:** 1 call per model with fixed dummy text (excluded from statistics)

## Statistics per model

| Metric | qwen3:8b | llama3.2 |
|---|---|---|
| p50 latency (ms) | 22935 | 3672 |
| p95 latency (ms) | 28611 | 4900 |
| % invalid (1st attempt) | 0.0% | 0.0% |
| % invalid (after retry) | 0.0% | 0.0% |
| % timeouts | 0.0% | 0.0% |
| % is_article=true | 97.7% | 100.0% |
| % subtopic substituted | 2.4% (1 articles) | 48.8% (21 articles) |
| p50 tokens/s | 4.7 | 41.7 |
| think=false supported | yes | yes |

## Category distribution

| Category | qwen3:8b | llama3.2 |
|---|---|---|
| Conflicts & Security | 11 | 29 |
| International Relations | 12 | 1 |
| Society | 1 | 0 |
| Politics & Government | 12 | 9 |
| Health & Medicine | 1 | 0 |
| Economy & Finance | 2 | 2 |
| Energy | 3 | 0 |
| Business & Industry | 0 | 2 |

## Pairwise agreement

| Models | is_article match | main_category match |
|---|---|---|
| qwen3:8b vs llama3.2 | 97% (42/43) | 47% (20/42) |

## Per-article results

| Domain | Title | Lang | text_chars | extract_source | skip_reason | qwen3:8b | llama3.2 |
|---|---|---|---|---|---|---|---|
| www.spiegel.de | (S+) Iran-Krieg: So läuft die doppelte Blockade der Straße von Hormus | de | 1285 | article |  | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/General |
| newsletter-tracking.dw.com |  | de | 110 | fallback | too-short | SKIP | SKIP |
| www.bbc.com | BBC Home \| Breaking News, World News, US News, Sports, Business, Innovation, Climate, Culture, Travel, Video & Audio | en | 2000 | article | root-url | SKIP | SKIP |
| www.theguardian.com | US and Iran in indirect talks to extend two-week ceasefire | en | 2000 | article |  | International Relations/Diplomacy & summits | Conflicts & Security/General |
| www.spiegel.de | (S+) Work-Life-Balance: Wie Sie trotz 40-Stunden-Woche Zeit für sich finden | de | 1233 | article |  | Society/General | Business & Industry/Companies & earnings |
| newsletter-tracking.dw.com |  | de | 110 | fallback | too-short | SKIP | SKIP |
| www.bbc.com | BBC Home \| Breaking News, World News, US News, Sports, Business, Innovation, Climate, Culture, Travel, Video & Audio | en | 2000 | article | root-url | SKIP | SKIP |
| us18.campaign-archive.com | Today's most read articles on Haaretz | en | 1342 | fallback | root-url | SKIP | SKIP |
| www.theguardian.com | US extends waiver allowing countries to buy Russian oil – as it happened | en | 2000 | article |  | International Relations/Diplomacy & summits | Conflicts & Security/General |
| view.angebote.spiegel.de |  | en | 85 | fallback | too-short | SKIP | SKIP |
| newsmaker.md | (ВИДЕО) Политпредставители Кишинева и Тирасполя обменялись резкими репликами во время встречи «1+1» | ru | 2000 | p-container |  | International Relations/Diplomacy & summits | Conflicts & Security/Cyber & hybrid threats |
| www.spiegel.de | Iran öffnet Straße von Hormus, USA bleiben bei Blockade | de | 2000 | article |  | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/General |
| www.spiegel.de | Meinung: News des Tages: Iran gibt Straße von Hormus frei, AfD überholt CDU, Waldrama vor Poel | de | 2000 | article |  | International Relations/Diplomacy & summits | Conflicts & Security/General |
| newsletter-tracking.dw.com |  | de | 110 | fallback | too-short | SKIP | SKIP |
| newsletter-tracking.dw.com |  | de | 110 | fallback | too-short | SKIP | SKIP |
| www.dw.com | Война в Украине | ru | 447 | p-container |  | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/General |
| www.dw.com | News and current affairs from Germany and around the world | en | 329 | p-container |  | Politics & Government/Elections | Politics & Government/Elections |
| us18.campaign-archive.com | The state of Israel: Trump says Israel ‘PROHIBITED’ from bombing Lebanon as cease-fire takes effect | en | 2000 | fallback | root-url | SKIP | SKIP |
| www.spiegel.de | Iran droht mit Schließung der Straße von Hormus – Trump mit einem Ende der Waffenruhe | de | 2000 | article |  | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/General |
| www.spiegel.de | (S+) David gegen Goliath: Ein Krebsmediziner legt sich mit einem globalen Pharmariesen an | de | 1209 | article |  | Health & Medicine/Diseases & outbreaks | Business & Industry/Companies & earnings |
| newsmaker.md | Зарплаты растут быстрее экономики. Что еще происходит с экономикой Молдовы?  | ru | 2000 | p-container |  | Economy & Finance/Macroeconomy & inflation | Economy & Finance/Macroeconomy & inflation |
| www.spiegel.de | Iran schließt die Straße von Hormus erneut | de | 2000 | article |  | International Relations/Diplomacy & summits | Conflicts & Security/General |
| email.useinsider.com | 404 - Page Not Found | en | 200 | p-container | not-found | SKIP | SKIP |
| www.spiegel.de | Iran, USA und die Waffenruhe: Teheran sieht weiter Differenzen mit Washington | de | 2000 | article |  | International Relations/Diplomacy & summits | Conflicts & Security/General |
| www.spiegel.de | Iran, USA und die Waffenruhe: Teheran sieht weiter Differenzen mit Washington | de | 2000 | article |  | International Relations/Diplomacy & summits | Conflicts & Security/General |
| us18.campaign-archive.com | Inside the Israel-Hezbollah truce in Lebanon and the gaps that could unravel it | en | 1777 | fallback | root-url | SKIP | SKIP |
| www.spiegel.de | Iran-Krieg: Donald Trump kündigt weitere Verhandlungen in Pakistan an | de | 2000 | article |  | International Relations/Diplomacy & summits | Conflicts & Security/General |
| email.useinsider.com | 404 - Page Not Found | en | 200 | p-container | not-found | SKIP | SKIP |
| newsletter-tracking.dw.com |  | de | 110 | fallback | too-short | SKIP | SKIP |
| www.spiegel.de | Irankrieg: Teheran droht mit Vergeltung für US-Beschuss von Frachtschiff | de | 2000 | article |  | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/Interstate wars |
| www.theguardian.com | US military seizes Iran-flagged ship trying to pass strait of Hormuz blockade | en | 2000 | article |  | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/Interstate wars |
| www.spiegel.de | Irankrieg: Teheran droht mit Vergeltung für US-Beschuss von Frachtschiff | de | 2000 | article |  | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/Interstate wars |
| www.bbc.com | BBC Home \| Breaking News, World News, US News, Sports, Business, Innovation, Climate, Culture, Travel, Video & Audio | en | 2000 | article | root-url | SKIP | SKIP |
| www.bbc.com | BBC News - Breaking news, video and the latest top stories from the U.S. and around the world | en | 2000 | article |  | Politics & Government/Elections | Politics & Government/General |
| email.useinsider.com | 404 - Page Not Found | en | 200 | p-container | not-found | SKIP | SKIP |
| newsletter-tracking.dw.com |  |  | 0 | fallback | not-found | SKIP | SKIP |
| www.theguardian.com | Virginia voters approve new congressional maps in blow to Trump | en | 2000 | article |  | Politics & Government/Parties & leaders | Politics & Government/Elections |
| newsmaker.md | Еврокомиссар Марта Кос оценила прогресс Молдовы на пути в ЕС | ru | 2000 | p-container |  | International Relations/Diplomacy & summits | Politics & Government/General |
| mailchi.mp | NM Espresso 22 апреля: чем в Молдове заменят ЧП, что будет с налогом на недвижимость, и ждут ли Румынию новые выборы | ru | 2000 | p-container |  | International Relations/Diplomacy & summits | Conflicts & Security/General |
| newsmaker.md | В Молдове ЧП в энергетике могут заменить на режим повышенной готовности | ru | 2000 | p-container |  | Energy/Energy prices & supply routes | Conflicts & Security/General |
| www.theguardian.com | Kim Jong-un offers Putin ‘full support’ in Russia’s ‘sacred fight’ with west | en | 2000 | article |  | International Relations/Diplomacy & summits | International Relations/Diplomacy & summits |
| www.theguardian.com | ‘Impossible’ to reopen strait of Hormuz amid ‘flagrant’ ceasefire breaches, Iran says | en | 2000 | article |  | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/Interstate wars |
| www.spiegel.de | (S+) Irankrieg als Scheidungsgrund: Tucker Carlson rechnet mit Donald Trump ab | de | 1257 | article |  | Politics & Government/Parties & leaders | Politics & Government/Protests & civil society |
| www.spiegel.de | (S+) Schwedt-Raffinerie: Das wären die Folgen einer russischen Ölblockade | de | 1204 | article |  | Energy/Oil & gas | Conflicts & Security/General |
| www.theguardian.com | Trump claims US has total control over strait of Hormuz after Iran seizes two container ships | en | 2000 | article |  | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/General |
| www.spiegel.de | (S+) Marine-Staatssekretär John Phlean: Donald Trump schwärmte von ihm – dennoch muss er nun gehen | de | 1239 | article |  | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/Military & defense |
| newsmaker.md | Глава полиции о требовании оппозиции уйти в отставку: «Подчинюсь, если решение примет мое руководство» | ru | 2000 | p-container |  | Politics & Government/Parties & leaders | Conflicts & Security/Terrorism |
| newsmaker.md | Драгалин поздравила бывших коллег из прокуратуры после приговора Плахотнюку: «Я знаю изнутри, сколько труда за этим стоит» | ru | 2000 | p-container |  | Politics & Government/Parties & leaders | Conflicts & Security/Terrorism |
| newsmaker.md | ЧП в цифрах. Что происходит в энергетике Молдовы после кризиса? | ru | 2000 | p-container |  | Energy/Energy prices & supply routes | Conflicts & Security/Interstate wars |
| email.useinsider.com | 404 - Page Not Found | en | 200 | p-container | not-found | SKIP | SKIP |
| email.useinsider.com | 404 - Page Not Found | en | 200 | p-container | not-found | SKIP | SKIP |
| www.spiegel.de | (S+) Haushalt 2027: Abgaben auf Zucker, Plastik, Tabak – so will die Regierung Milliarden reinholen | de | 1227 | article |  | Economy & Finance/Public finance & taxes | Economy & Finance/Macroeconomy & inflation |
| www.spiegel.de | (S+) Warum die Rede von König Charles im US-Kongress ein diplomatisches Meisterstück war - die Analyse | de | 1309 | article |  | International Relations/Diplomacy & summits | Politics & Government/General |
| www.theguardian.com | Trump calls Iran’s response to peace plan ‘totally unacceptable’ as ceasefire frays | en | 2000 | article |  | Politics & Government/Parties & leaders | Conflicts & Security/General |
| www.theguardian.com | Democrats express ‘grave concerns’ over secretive ICE deportation flights | en | 2000 | article |  | Politics & Government/Parties & leaders | Conflicts & Security/Terrorism |
| www.theguardian.com | Trump says ceasefire with Iran on ‘life support’ after rejecting peace proposals | en | 2000 | article |  | Conflicts & Security/Civil wars & insurgencies | Conflicts & Security/General |
| newsmaker.md | Фуртунэ через суд требует признать «захват власти правящей партией». Реакция PAS | ru | 2000 | p-container |  | Politics & Government/Parties & leaders | Conflicts & Security/Terrorism |
| newsmaker.md | Гросу не планирует уходить с поста председателя PAS: Я баллотируюсь на новый срок | ru | 2000 | p-container |  | Politics & Government/Parties & leaders | Politics & Government/Government & legislation |
| www.theguardian.com | Vostochny cosmodrome: the remote Russian spaceport hosting Kim and Putin | en | 2000 | article |  | Politics & Government/Parties & leaders | Conflicts & Security/General |
| www.bbc.com | US politics - BBC News | en | 1011 | article |  | Politics & Government/Elections | Politics & Government/Elections |
| www.spiegel.de | (S+) US-Präsident in China: Symbolik schlägt Substanz – fünf Gipfelerkenntnisse | de | 1148 | article |  | not-article | Politics & Government/General |
| view.angebote.spiegel.de |  | en | 98 | fallback | root-url | SKIP | SKIP |

## Accuracy vs gold

*(gold from external file)*

Gold labels filled: 61 / 62 rows

| Model | is_article accuracy | main_category accuracy |
|---|---|---|
| qwen3_8b | 88.5% (54/61) | 75.0% (27/36) |
| llama3_2 | 90.2% (55/61) | 37.8% (14/37) |
