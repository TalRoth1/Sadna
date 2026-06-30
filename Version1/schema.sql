CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('LOGGED_IN', 'NOT_LOGGED_IN', 'REMOVED')),
    age INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE purchase_policies (
    id UUID PRIMARY KEY
);

CREATE TABLE discount_policies (
    id UUID PRIMARY KEY
);

CREATE TABLE rules (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL,
    rule_type VARCHAR(50) NOT NULL CHECK (rule_type IN ('AGE_RULE', 'LONE_SEAT_RULE', 'MAX_TICKET_RULE', 'MIN_TICKET_RULE', 'COMPOSITE')),
    parameters JSONB NOT NULL,
    FOREIGN KEY (policy_id) REFERENCES purchase_policies(id) ON DELETE CASCADE
);

CREATE TABLE discounts (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL,
    discount_type VARCHAR(50) NOT NULL CHECK (discount_type IN ('CONDITIONAL_DISCOUNT', 'COUPON_CODE_DISCOUNT', 'OVERT_DISCOUNT')),
    from_date TIMESTAMP NOT NULL,
    to_date TIMESTAMP NOT NULL,
    parameters JSONB NOT NULL,
    FOREIGN KEY (policy_id) REFERENCES discount_policies(id) ON DELETE CASCADE
);

CREATE TABLE companies (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    founder_username VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    discount_policy_id UUID,
    purchase_policy_id UUID,
    FOREIGN KEY (founder_username) REFERENCES users(username),
    FOREIGN KEY (discount_policy_id) REFERENCES discount_policies(id) ON DELETE SET NULL,
    FOREIGN KEY (purchase_policy_id) REFERENCES purchase_policies(id) ON DELETE SET NULL
);

CREATE TABLE company_members (
    company_id UUID NOT NULL,
    username VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'FOUNDER')),
    appointer_username VARCHAR(100), -- Fixed typo from 'appointer' to match the FK below
    permissions JSONB NOT NULL,
    PRIMARY KEY (company_id, username),
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (company_id, appointer_username) REFERENCES company_members(company_id, username) ON DELETE SET NULL
);

CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    appointer_username VARCHAR(100) NOT NULL,
    apointee_username VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'MANAGER')),
    permissions JSONB NOT NULL,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    FOREIGN KEY (appointer_username) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (apointee_username) REFERENCES users(username) ON DELETE CASCADE
);

CREATE TABLE lotteries (
    id UUID PRIMARY KEY,
    registration_open TIMESTAMP NOT NULL,
    registration_close TIMESTAMP NOT NULL
);

CREATE TABLE lottery_entries (
    lottery_id UUID NOT NULL,
    username VARCHAR(100) NOT NULL,
    requested_ticket_amount INT NOT NULL,
    FOREIGN KEY (lottery_id) REFERENCES lotteries(id) ON DELETE CASCADE,
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE,
    PRIMARY KEY (lottery_id, username)
);

CREATE TABLE lottery_winners (
    lottery_id UUID NOT NULL,
    username VARCHAR(100) NOT NULL,
    requested_ticket_amount INT NOT NULL,
    access_code VARCHAR(255) NOT NULL UNIQUE,
    FOREIGN KEY (lottery_id) REFERENCES lotteries(id) ON DELETE CASCADE,
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE,
    PRIMARY KEY (lottery_id, username)
);

CREATE TABLE layout (
    id UUID PRIMARY KEY,
    map_image TEXT NOT NULL
);

CREATE TABLE event_areas (
    id UUID PRIMARY KEY,
    layout_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('SEATING', 'STANDING')),
    price FLOAT NOT NULL
);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    row INT NOT NULL,
    number INT NOT NULL
);

-- Moved EVENTS up here because other tables depend on it!
CREATE TABLE events (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    company_id UUID NOT NULL,
    manager_username VARCHAR(100) NOT NULL,
    location VARCHAR(255) NOT NULL,
    description TEXT,
    tags JSONB,
    artist VARCHAR(255),
    type VARCHAR(255),
    date TIMESTAMP NOT NULL,
    rating FLOAT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'ENDED', 'CANCELED')),
    layout_id UUID,
    lottery_id UUID,
    discount_policy_id UUID,
    purchase_policy_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    FOREIGN KEY (manager_username) REFERENCES users(username) ON DELETE SET NULL,
    FOREIGN KEY (layout_id) REFERENCES layout(id) ON DELETE SET NULL,
    FOREIGN KEY (lottery_id) REFERENCES lotteries(id) ON DELETE SET NULL,
    FOREIGN KEY (discount_policy_id) REFERENCES discount_policies(id) ON DELETE SET NULL,
    FOREIGN KEY (purchase_policy_id) REFERENCES purchase_policies(id) ON DELETE SET NULL
);

CREATE TABLE active_purchases (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    event_id UUID NOT NULL,
    end_time TIMESTAMP NOT NULL,
    is_guest_confirmed_age BOOLEAN NOT NULL DEFAULT FALSE,
    copon VARCHAR(255),
    price FLOAT NOT NULL,
    max_wait_time FLOAT NOT NULL,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);

CREATE TABLE purchase_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    event_id UUID NOT NULL,
    purchase_info JSONB NOT NULL,
    purchase_total FLOAT NOT NULL,
    purchase_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    issued_ticket_ref VARCHAR(255),
    payment_transaction_id INTEGER,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);

CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    area_id UUID NOT NULL,
    user_id UUID, -- Added missing column definition
    seat_id UUID,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD')),
    price FLOAT NOT NULL,
    active_purchase_id UUID,
    purchase_history_id UUID,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    FOREIGN KEY (area_id) REFERENCES event_areas(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (seat_id) REFERENCES seats(id) ON DELETE SET NULL
);

ALTER TABLE tickets 
ADD CONSTRAINT chk_ticket_purchase_exclusivity
CHECK (
    (active_purchase_id IS NOT NULL AND purchase_history_id IS NULL) OR
    (active_purchase_id IS NULL AND purchase_history_id IS NOT NULL) OR
    (active_purchase_id IS NULL AND purchase_history_id IS NULL)
);

CREATE TABLE admins (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);

CREATE TABLE admin_action_logs (
    id UUID PRIMARY KEY,
    admin_id UUID NOT NULL,
    admin_username VARCHAR(100) NOT NULL,
    action_type TEXT NOT NULL,
    target_id TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (admin_id) REFERENCES admins(id) ON DELETE CASCADE,
    FOREIGN KEY (admin_username) REFERENCES users(username) ON DELETE CASCADE
);

CREATE TABLE admin_complaints (
    id UUID PRIMARY KEY,
    reporter_user_id UUID NOT NULL,
    reporter_username VARCHAR(100) NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    admin_complaint_status VARCHAR(20) NOT NULL CHECK (admin_complaint_status IN ('OPEN', 'ANSWERED', 'CLOSED')),
    admin_response TEXT,
    responser_admin_usrname VARCHAR(100),
    responded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (reporter_user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (reporter_username) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (responser_admin_usrname) REFERENCES users(username) ON DELETE SET NULL
);

CREATE TABLE ratings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    event_id UUID,
    company_id UUID,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    review TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

ALTER TABLE ratings
ADD CONSTRAINT chk_rating_association_exclusivity
CHECK (
    (event_id IS NOT NULL AND company_id IS NULL) OR
    (event_id IS NULL AND company_id IS NOT NULL)
);

CREATE UNIQUE INDEX idx_unique_user_event_rating 
ON ratings (user_id, event_id) 
WHERE event_id IS NOT NULL;

CREATE UNIQUE INDEX idx_unique_user_company_rating
ON ratings (user_id, company_id) 
WHERE company_id IS NOT NULL;

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL CHECK (notification_type IN ('PURCHASE_COMPLETED', 'EVENT_CHANGED', 'EVENT_CANCELLED', 'SOLD_OUT', 'COMPANY_CLOSED', 'ROLE_CHANGED', 'ACTIVE_PURCHASE_EXPIRING', 'ADMIN_MESSAGE', 'LOTTERY_WON', 'QUEUE_ACCESS_GRANTED', 'GENERAL')),
    message TEXT NOT NULL,
    target_url TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
);